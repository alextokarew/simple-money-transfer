package com.github.alextokarew.moneytransfer.process

import java.time.Clock
import java.util.Random
import java.util.concurrent.CountDownLatch

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.github.alextokarew.moneytransfer.domain._
import com.github.alextokarew.moneytransfer.storage.InMemoryStorage
import org.scalatest.{Matchers, WordSpec}

class ProcessorIntegrationSpec extends WordSpec with Matchers {

  private val clock = Clock.systemUTC()
  private val nAccounts = 1000
  private val nTransfers = 1000000

  private val accountStorage = InMemoryStorage[AccountId, Account]()
  private val balanceStorage = InMemoryStorage[AccountId, BigInt]()
  private val transferUpdateCounter = new CountDownLatch(nTransfers)

  private val transferStorage = new InMemoryStorage[Long, Transfer]() {
    override def update(id: Long, doUpdate: Transfer => Transfer): Option[Transfer] = {
      val result = super.update(id, doUpdate)
      transferUpdateCounter.countDown()
      result
    }
  }

  private val maxAccountBalance = 1000000
  private val maxTransferAmount = 100000

  private def totalBalance(): BigInt =
    (1 to nAccounts).foldLeft(0: BigInt)((acc, n) => acc + balanceStorage.get(accountId(n)).get)

  private def createSampleAccounts(random: Random): Unit = {
    (1 to nAccounts).foreach { n =>
      val id = accountId(n)
      val balance: BigInt = random.nextInt(maxAccountBalance)
      val maxLimit = Some(balance * 2).filter(_ => random.nextBoolean())
      val account = Account(id, s"Sample account $n", maxLimit)

      accountStorage.putIfAbsent(id, account)
      balanceStorage.putIfAbsent(id, balance)
    }
  }

  private def createSampleTransfers(random: Random): Unit = {
    (1 to nTransfers).foreach { n =>
      val id = n
      val token = s"token-$n"
      val from = accountId(random.nextInt(nAccounts) + 1)
      def to(): AccountId = {
        val accId = accountId(random.nextInt(nAccounts) + 1)
        if (accId == from) to() else accId
      }
      val amount = random.nextInt(maxTransferAmount) + 1
      val created = clock.millis()

      val transfer = Transfer(id, TransferRequest(from, to(), amount, None, token), Processing, created, created)
      transferStorage.putIfAbsent(id, transfer)
    }
  }

  "Money transfer processor" should {
    "Process a lot of concurrent operations and keep all invariants" in {
      implicit val system: ActorSystem = ActorSystem("simpleMoneyTransferIT")
      implicit val materializer: ActorMaterializer = ActorMaterializer()

      val processor = Processor(
        Runtime.getRuntime.availableProcessors(),
        accountStorage,
        balanceStorage,
        transferStorage,
        clock)

      val random = new Random()

      //Assume we have 1K random accounts
      createSampleAccounts(random)

      val initialTotalBalance = totalBalance()

      //...and 1M transfers
      createSampleTransfers(random)

      val concurrentRequestsNum = Runtime.getRuntime.availableProcessors()

      def startProcess(modulo: Int) = {
        Source(1 to nTransfers).filter(_ % concurrentRequestsNum == modulo).runForeach { transferId =>
          processor.enqueue(transferStorage.get(transferId).get)
        }
      }

      //Imitating concurrent independent transfer requests
      (0 until concurrentRequestsNum).foreach(startProcess)

      //Awaiting while all transfers are being processed
      transferUpdateCounter.await()

      //The total sum of balances must remain the same
      val resultTotalBalance = totalBalance()
      resultTotalBalance shouldEqual initialTotalBalance

      //All transfers must be in one of the final statuses
      (1 to nTransfers).foreach { transferId =>
        transferStorage.get(transferId).exists(t => t.status != Processing)
      }

      system.terminate()
    }
  }

  private def accountId(n: Int) = AccountId(s"test-$n")
}
