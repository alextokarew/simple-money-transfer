package com.github.alextokarew.moneytransfer.process

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{AtomicInteger, AtomicLongArray, AtomicReferenceArray}

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.github.alextokarew.moneytransfer.domain._
import com.github.alextokarew.moneytransfer.storage.Storage
import com.github.alextokarew.moneytransfer.validation.Validation._
import com.typesafe.scalalogging.LazyLogging
import org.reactivestreams.{Publisher, Subscriber, Subscription}

/**
  * Money transfer operation processor.
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
    * This class is the main entry point and it serves as master. It acts as publisher of incoming transfers which are
    * pulled by workers.
    *
    * @see  <a href="http://www.reactive-streams.org/">reactive-streams</a> specification
    * @param numWorkers the total number of workers
    * @param balanceStorage account balances storage
    * @param transferStorage transfer operations storage
    * @return
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

  private val registeredWorkersCount = new AtomicInteger(0)

  //here we use atomic arrays instead of maps because we assume that workers are indexed by integers
  private val workers = new AtomicReferenceArray[Subscriber[_ >: Transfer]](numWorkers)
  private val workerRequests = new AtomicLongArray(numWorkers)

  //Assotiating an account with a certain worker so that all operations on this account will happen sequentially
  //AccountId -> (WorkerId, count)
  private val accountIdToWorker = new ConcurrentHashMap[AccountId, AccountToWorker]()

  override def enqueue(transfer: Transfer): Unit = findWorker(transfer) match {
    case Some(workerId) => push(workerId, transfer)
    case None => //push to the buffer
  }

  def onComplete(transfer: Transfer): Unit = {
    //Decrement transfers count for the from and to sides
    //Try to push some transfers that are waiting in the buffer
    ???
  }

  /**
    * Finds an appropriate worker by source and destination account id
    * @return Some(workerId) if the worker was found or None if there is no worker that can handle this transfer
    */
  private def findWorker(transfer: Transfer): Option[Int] = {
    val fromWorkerOpt = Option(accountIdToWorker.get(transfer.request.from))
    val toWorkerOpt = Option(accountIdToWorker.get(transfer.request.to))

    val workerIdOpt = (fromWorkerOpt, toWorkerOpt) match {
      //IF from and to associated with one worker AND this worker requests > 0 then return it
      case (Some(w1), Some(w2)) if w1.workerId == w2.workerId => Some(w1.workerId)

      //ELSE IF one of the sides is associated and the other is not, and this worker requests > 0 - return it
      case (Some(w), None) => Some(w.workerId)
      case (None, Some(w)) => Some(w.workerId)

      //ELSE IF no worker is associated with any of the sides return worker with maximum requests number
      case (None, None) => findWorkerWithMaxDemand()

      case _ => None
    }

    val result = workerIdOpt.filter(workerId => workerRequests.get(workerId) > 0)

    //IF worker is assigned then bound both sides to the worker
    workerIdOpt.foreach { workerId =>
      accountIdToWorker.putAll()
    }

    workerIdOpt

    ???
  }

  private def findWorkerWithMaxDemand(): Option[Int] = ???

  private def push(workerId: Int, transfer: Transfer): Unit = {
    logger.debug("Transfer {} is being pushed to worker {}", transfer.id, workerId)
    workers.get(workerId).onNext(transfer)
    workerRequests.decrementAndGet(workerId)
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
      workerRequests.addAndGet(workerId, n)
    }

    override def cancel(): Unit = {
      //In the current version of the service this method is invoked only during the system shutdown, so no implementation is needed.
      logger.debug("A worker {} has requested the cancellation", workerId)
    }
  }
}

private case class AccountToWorker(workerId: Int, count: Int)

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
            logger.debug("Transfer {} was completed succesfully")

          case Left(errors) =>
            transferStorage.update(transfer.id, _.copy(status = Failed(errors), updated = clock.millis()))
            logger.debug("Transfer {} was failed with following errors: {}", transfer.id, errors)
        }

        val updatedTransfer = transferStorage.get(transfer.id).get
        master.onComplete(updatedTransfer)
      }
  }
}
