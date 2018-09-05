package com.github.alextokarew.moneytransfer.service

import java.time.Clock
import java.util.concurrent.atomic.AtomicLong

import com.github.alextokarew.moneytransfer.domain._
import com.github.alextokarew.moneytransfer.process.Processor
import com.github.alextokarew.moneytransfer.storage.Storage
import com.github.alextokarew.moneytransfer.validation.Validation._
import com.typesafe.scalalogging.LazyLogging

trait TransferService {

  /**
    * Creater a money transfer according to request
    * @param request money transfer
    * @return a new transfer
    */
  def createTransfer(request: TransferRequest): Valid[Transfer]
  def getTransfer(id: Long): Valid[Transfer]
}

class TransferServiceImpl(
  processor: Processor,
  accountStorage: Storage[AccountId, Account],
  tokenStorage: Storage[String, Long],
  transferStorage: Storage[Long, Transfer],
  clock: Clock) extends TransferService with LazyLogging {

  private val idGenerator = new AtomicLong(0)

  override def createTransfer(request: TransferRequest): Valid[Transfer] = {
    logger.debug("Trying to create new money transfer operation according to request {}", request)
    validate(request)(
      check(r => accountStorage.exists(r.from), "Source account does not exist"),
      check(r => accountStorage.exists(r.to), "Destination account does not exist"),
      check(_.amount > 0, "An amount to transfer must be strictly positive")
    ).map { validRequest =>
      logger.debug("Transfer request {} was succesfully validated", validRequest)
      val id = tokenStorage.putIfAbsent(validRequest.token, idGenerator.incrementAndGet())
      logger.debug("Transfer id for token {} is {}", validRequest.token, id)

      val createdTime = clock.millis()
      val transfer = Transfer(id, validRequest, Processing, createdTime, createdTime)
      transferStorage.putIfAbsent(id, transfer, Some(t => processor.enqueue(t)))
    }
  }

  override def getTransfer(id: Long): Valid[Transfer] = validate(transferStorage.get(id))(
    check(_.isDefined, s"Transfer with id $id does not exist")
  ).map(_.get)
}

object TransferServiceImpl {
  def apply(
    processor: Processor,
    accountStorage: Storage[AccountId, Account],
    tokenStorage: Storage[String, Long],
    transferStorage: Storage[Long, Transfer],
    clock: Clock
  ): TransferServiceImpl = new TransferServiceImpl(processor, accountStorage, tokenStorage, transferStorage, clock)
}