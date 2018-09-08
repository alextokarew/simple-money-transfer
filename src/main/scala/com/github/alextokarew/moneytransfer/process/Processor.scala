package com.github.alextokarew.moneytransfer.process

import java.time.Clock
import java.util.concurrent.{ConcurrentHashMap, LinkedBlockingQueue}
import java.util.concurrent.atomic.{AtomicInteger, AtomicLongArray, AtomicReferenceArray}

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.github.alextokarew.moneytransfer.domain._
import com.github.alextokarew.moneytransfer.storage.Storage
import com.github.alextokarew.moneytransfer.validation.Validation._
import com.typesafe.scalalogging.LazyLogging
import org.reactivestreams.{Publisher, Subscriber, Subscription}


/**
  * Money transfer operation processor. It must ensure that for any account there are no concurrent transfers that are
  * being processed at the same time.
  */
trait Processor {
  /**
    * Enqueues a transfer operation to process.
    * @param transfer a transfer to process
    */
  def enqueue(transfer: Transfer)
}

object Processor {

  /**
    * Creates a processor that is implemented using master-worker pattern and reactive-streams principle.
    *
    * @see  <a href="http://www.reactive-streams.org/">reactive-streams</a> specification
    * @param numWorkers the total number of workers
    * @param accountStorage account details storage
    * @param balanceStorage account balances storage
    * @param transferStorage transfer operations storage
    * @param clock clock that is used to calculate updated time
    * @return an implementation of [[Processor]] trait
    */
  def apply(
    numWorkers: Int,
    accountStorage: Storage[AccountId, Account],
    balanceStorage: Storage[AccountId, BigInt],
    transferStorage: Storage[Long, Transfer],
    clock: Clock)(implicit materializer: Materializer): Processor = {
    val master = new ProcessorImplMaster(numWorkers)
    val workerFactory = new ProcessorImplWorkerFactory(accountStorage, balanceStorage, transferStorage, clock)
    (1 to numWorkers).foreach(_ => workerFactory.createWorker(master))

    master
  }
}

/**
  * This class is the main entry point and it serves as master. It acts as a publisher of incoming transfers which are
  * pulled by workers.
  * @param numWorkers the total number of workers
  */
private class ProcessorImplMaster(numWorkers: Int) extends Processor with Publisher[Transfer] with LazyLogging {

  val waitingBuffer = new LinkedBlockingQueue[Transfer]()

  private val registeredWorkersCount = new AtomicInteger(0)

  //here we use atomic arrays instead of maps because we assume that workers are indexed by integers
  private val workers = new AtomicReferenceArray[Subscriber[_ >: Transfer]](numWorkers)
  private val workersDemand = new AtomicLongArray(numWorkers)

  //Here we associate an account with a certain worker so that all operations on this account will happen sequentially
  //We assume that the number of accounts is much greater than the number of workers, so we can push a transfer to
  //a certain worker and let the subsequent transfers for the same accounts wait until the current transfer is completed
  private val accountIdToWorker = new ConcurrentHashMap[AccountId, Integer]()

  override def enqueue(transfer: Transfer): Unit = findWorker(transfer) match {
    case Some(workerId) => push(workerId, transfer)
    case None =>
      logger.debug("Transfer {} has been put to the waiting buffer", transfer.id)
      waitingBuffer.put(transfer)
  }

  def onComplete(transfer: Transfer): Unit = {
    val workerId = accountIdToWorker.remove(transfer.request.from)
    accountIdToWorker.remove(transfer.request.to)
    drainWaitingBuffer(workerId)
  }

  private def drainWaitingBuffer(workerId: Int): Unit = {
    def iterate(demand: Long): Unit = {
      if (demand > 0) {
        val t: Transfer = waitingBuffer.poll()
        if (t != null) {
          enqueue(t)
          iterate(demand - 1)
        }
      }
    }

    iterate(workersDemand.get(workerId))
  }

  /**
    * Finds an appropriate worker by source and destination account id
    * @return Some(workerId) if the worker was found or None if there is no worker that can handle this transfer
    */
  private def findWorker(transfer: Transfer): Option[Int] = {
    if (accountIdToWorker.get(transfer.request.from) != null || accountIdToWorker.get(transfer.request.to) != null) {
      return None
    }

    val workerIdOpt = findWorkerWithMaxDemand()

    if (workerIdOpt.isEmpty) {
      return None
    }

    val workerId = workerIdOpt.get

    if (accountIdToWorker.putIfAbsent(transfer.request.from, workerId) == null) {
      if (accountIdToWorker.putIfAbsent(transfer.request.to, workerId) == null) {
        return Some(workerId)
      } else {
        accountIdToWorker.remove(transfer.request.from, workerId)
      }
    }

    findWorker(transfer)

  }

  private def findWorkerWithMaxDemand(): Option[Int] = {
    val (workerId, demand) = (1 until workersDemand.length()).foldLeft((0, workersDemand.get(0))) {
      case ((maxIndex, maxValue), index) =>
        val value = workersDemand.get(index)
        if (value > maxValue) index -> value else maxIndex -> maxValue
    }

    if (demand > 0) Some(workerId) else None
  }

  private def push(workerId: Int, transfer: Transfer): Unit = {
    logger.debug("Transfer {} has been pushed to worker {}", transfer.id, workerId)
    workers.get(workerId).onNext(transfer)
    workersDemand.decrementAndGet(workerId)
  }

  override def subscribe(worker: Subscriber[_ >: Transfer]): Unit = {
    val workerId = registeredWorkersCount.getAndIncrement()
    logger.debug("Registering worker with id {}", workerId)
    workers.set(workerId, worker)
    worker.onSubscribe(createSubscription(workerId))
  }

  private def createSubscription(workerId: Int): Subscription = new Subscription {
    override def request(n: Long): Unit = {
      logger.debug("A worker {} has requested {} transfers to process", workerId, n)
      workersDemand.addAndGet(workerId, n)
//      drainWaitingBuffer(workerId)
    }

    override def cancel(): Unit = {
      //In the current version of the service this method is invoked only during the system shutdown, so no implementation is needed.
      logger.debug("A worker {} has requested the cancellation", workerId)
    }
  }
}

private class ProcessorImplWorkerFactory(
  accountStorage: Storage[AccountId, Account],
  balanceStorage: Storage[AccountId, BigInt],
  transferStorage: Storage[Long, Transfer],
  clock: Clock) extends LazyLogging {

  private val fromAccountPredicate: Transfer => Boolean = { t =>
    balanceStorage.get(t.request.from).get >= t.request.amount
  }

  private val toAccountPredicate: Transfer => Boolean = { t =>
    val account = accountStorage.get(t.request.to).get
    val balance = balanceStorage.get(t.request.to).get
    !account.maxLimit.exists(_ < balance + t.request.amount)
  }

  def createWorker(master: ProcessorImplMaster)(implicit materializer: Materializer): Unit = {
    Source
      .fromPublisher(master)
      .runForeach { transfer =>
        logger.debug("Starting to process transfer {}", transfer.id)
        validate(transfer)(
          check(fromAccountPredicate, "Insufficient funds on source account"),
          check(toAccountPredicate, "Transfer is not possible because it will exceed maximum limit on target account")
        ) match {
          case Right(t) =>
            balanceStorage.update(t.request.from, _ - t.request.amount)
            balanceStorage.update(t.request.to, _ + t.request.amount)
            transferStorage.update(t.id, _.copy(status = Succeded, updated = clock.millis()))
            logger.debug("Transfer {} was completed succesfully", t.id)

          case Left(errors) =>
            transferStorage.update(transfer.id, _.copy(status = Failed(errors), updated = clock.millis()))
            logger.debug("Transfer {} was failed with following errors: {}", transfer.id, errors)
        }

        val updatedTransfer = transferStorage.get(transfer.id).get
        master.onComplete(updatedTransfer)
      }
  }
}
