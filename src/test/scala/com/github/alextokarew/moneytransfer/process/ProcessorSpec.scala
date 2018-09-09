package com.github.alextokarew.moneytransfer.process

import java.time.{Clock, Instant, ZoneId}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.{TestKit, TestProbe}
import com.github.alextokarew.moneytransfer.domain._
import com.github.alextokarew.moneytransfer.storage.InMemoryStorage
import com.github.alextokarew.moneytransfer.validation.ValidationError
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec, WordSpecLike}

import scala.concurrent.duration._

class ProcessorSpec extends TestKit(ActorSystem("ProcessorSpec")) with WordSpecLike with Matchers with BeforeAndAfterEach {
  import ProcessorSpec._

  private val sourceAccountId = AccountId("123-456-7890")
  private val sourceAccount = Account(sourceAccountId, "Source account", None)

  private val destinationAccountId = AccountId("123-456-7891")
  private val destinationAccount = Account(destinationAccountId, "Destination account", Some(20000))

  private val initialBalance: BigInt = 15000

  private val accountStorage = InMemoryStorage[AccountId, Account]()
  private val balanceStorage = InMemoryStorage[AccountId, BigInt]()

  accountStorage.putIfAbsent(sourceAccountId, sourceAccount)
  accountStorage.putIfAbsent(destinationAccountId, destinationAccount)
  balanceStorage.putIfAbsent(sourceAccountId, initialBalance)
  balanceStorage.putIfAbsent(destinationAccountId, initialBalance)

  private val transferRequestPrototype = TransferRequest(sourceAccountId, destinationAccountId, 3000, None, "token")
  private val trigger = TestProbe()

  private val transferStorage = new InMemoryStorage[Long, Transfer]() {
    override def update(id: Long, doUpdate: Transfer => Transfer): Option[Transfer] = {
      val result = super.update(id, doUpdate)
      trigger.ref ! TransferUpdated(id)
      result
    }
  }

  private val updated: Long = System.currentTimeMillis()
  private val created: Long = updated - 5000
  private val clock = Clock.fixed(Instant.ofEpochMilli(updated), ZoneId.of("UTC"))

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  private val processor: Processor = Processor(1, accountStorage, balanceStorage, transferStorage, clock)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    balanceStorage.update(sourceAccountId, _ => initialBalance)
    balanceStorage.update(destinationAccountId, _ => initialBalance)
  }

  private def createAndEnqueueTransfer(id: Long, amount: BigInt): Unit = {
    val transfer = Transfer(id, transferRequestPrototype.copy(amount = amount), Processing, created, created)
    transferStorage.putIfAbsent(transfer.id, transfer)
    processor.enqueue(transfer)
  }

  "Processor" should {
    "Transfer money when the conditions are valid" in {
      createAndEnqueueTransfer(3L, 3000)

      trigger.expectMsg(2.seconds, TransferUpdated(3L))

      val result = transferStorage.get(3L)
      result shouldEqual Some(Transfer(3L, transferRequestPrototype, Succeded, created, updated))
    }


    "Fail transfer" when {
      "source account dosen't have enough amount" in {
        balanceStorage.update(sourceAccountId, _ => 2000)
        createAndEnqueueTransfer(4L, 3000)

        trigger.expectMsg(2.seconds, TransferUpdated(4L))

        val result = transferStorage.get(4L)
        result shouldEqual Some(Transfer(
          4L,
          transferRequestPrototype.copy(amount = 3000),
          Failed(List(ValidationError("Insufficient funds on source account"))),
          created,
          updated
        ))
      }

      "destination account's max limit will be exceeded" in {
        createAndEnqueueTransfer(5L, 7000)

        trigger.expectMsg(2.seconds, TransferUpdated(5L))

        val result = transferStorage.get(5L)
        result shouldEqual Some(Transfer(
          5L,
          transferRequestPrototype.copy(amount = 7000),
          Failed(List(ValidationError("Transfer is not possible because it will exceed maximum limit on target account"))),
          created,
          updated
        ))
      }
    }
  }
}

object ProcessorSpec {
  case class TransferUpdated(id: Long)
}
