package com.github.alextokarew.moneytransfer.service

import java.time.{Clock, Instant, ZoneId}

import com.github.alextokarew.moneytransfer.domain._
import com.github.alextokarew.moneytransfer.process.Processor
import com.github.alextokarew.moneytransfer.storage.{InMemoryStorage, Storage}
import com.github.alextokarew.moneytransfer.validation.ValidationError
import org.mockito.Mockito
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}

class TransferServiceSpec extends WordSpec with Matchers with BeforeAndAfterEach with MockitoSugar {

  private val accountId1 = AccountId("123-456-7890")
  private val account1 = Account(accountId1, "Account 1", None)
  private val accountId2 = AccountId("123-456-7891")
  private val account2 = Account(accountId2, "Account 2", None)
  private val nonExistingAccountId = AccountId("987-654-3210")

  private val existingTransferId = 1L
  private val existingTransferToken = "token-1"
  private val existingTransfer = Transfer(
    existingTransferId,
    TransferRequest(accountId1, accountId2, 1000, Some("Sample transfer"), existingTransferToken),
    Succeded,
    System.currentTimeMillis(),
    System.currentTimeMillis()
  )

  private val now = System.currentTimeMillis()
  private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneId.of("UTC"))
  private val processor: Processor = mock[Processor]

  private var accountStorage: Storage[AccountId, Account] = _
  private var tokenStorage: Storage[String, Long] = _
  private var transferStorage: Storage[Long, Transfer] = _
  private var service: TransferService = _

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    accountStorage = InMemoryStorage()
    accountStorage.putIfAbsent(accountId1, account1)
    accountStorage.putIfAbsent(accountId2, account2)

    tokenStorage = InMemoryStorage()
    tokenStorage.putIfAbsent(existingTransferToken, existingTransferId)

    transferStorage = InMemoryStorage()
    transferStorage.putIfAbsent(existingTransferId, existingTransfer)

    Mockito.reset(processor)

    service = TransferServiceImpl(processor, accountStorage, tokenStorage, transferStorage, clock, existingTransferId)
  }


  "TransferService#createTransfer" should {
    "create a transfer according to specified transfer request and send it to a processor" in {
      val request = TransferRequest(accountId1, accountId2, 2000, Some("Another transfer"), "token-2")

      val result = service.createTransfer(request)
      val expectedTransfer = Transfer(2L, request, Processing, now, now)
      result shouldEqual Right(expectedTransfer)
      verify(processor).enqueue(expectedTransfer)
    }

    "return an existing transfer if the token was used before and don't send it to a processor" in {
      val request = TransferRequest(accountId1, accountId2, 2000, Some("Sample transfer retry"), existingTransferToken)

      val result = service.createTransfer(request)
      result shouldEqual Right(existingTransfer)
      verify(processor, never()).enqueue(any())
    }

    "return validation error" when {
      "from account does not exist" in {
        val request = TransferRequest(nonExistingAccountId, accountId2, 2000, None, "token-2")

        val result = service.createTransfer(request)
        result shouldEqual Left(List(ValidationError("Source account does not exist")))
      }

      "to account does not exist" in {
        val request = TransferRequest(accountId1, nonExistingAccountId, 2000, None, "token-2")

        val result = service.createTransfer(request)
        result shouldEqual Left(List(ValidationError("Destination account does not exist")))
      }

      "from and to accounts are the same" in {
        val request = TransferRequest(accountId1, accountId1, 2000, None, "token-2")

        val result = service.createTransfer(request)
        result shouldEqual Left(List(ValidationError("Source and destination accounts must be different")))
      }

      "amount to transfer is non-positive" in {
        val request = TransferRequest(accountId1, accountId2, -2000, None, "token-2")

        val result = service.createTransfer(request)
        result shouldEqual Left(List(ValidationError("An amount to transfer must be strictly positive")))
      }
    }
  }


  "TransferService#getTransfer" should {
    "retrieve an existing transfer by id" in {
      val result = service.getTransfer(existingTransferId)

      result shouldEqual Right(existingTransfer)
    }

    "return an error when transfer id does not exist" in {
      val result = service.getTransfer(999L)

      result shouldEqual Left(List(ValidationError(s"Transfer with id 999 does not exist")))
    }
  }


}
