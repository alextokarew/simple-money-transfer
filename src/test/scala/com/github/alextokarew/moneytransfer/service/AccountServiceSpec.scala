package com.github.alextokarew.moneytransfer.service

import com.github.alextokarew.moneytransfer.domain.{Account, AccountId, Balance}
import com.github.alextokarew.moneytransfer.storage.{InMemoryStorage, Storage}
import com.github.alextokarew.moneytransfer.validation.ValidationError
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}

class AccountServiceSpec extends WordSpec with Matchers with BeforeAndAfterEach {

  val existingAccountId = AccountId("123-456-7890")
  val existingAccount = Account(existingAccountId, "Existing account", None)
  val newAccountId = AccountId("987-654-3210")
  val newAccountPrototype = Account(newAccountId, "New account", None)

  var accountStorage: Storage[AccountId, Account] = _
  var balanceStorage: Storage[AccountId, BigInt] = _
  var service: AccountService = _


  override protected def beforeEach(): Unit = {
    super.beforeEach()
    accountStorage = InMemoryStorage()
    balanceStorage = InMemoryStorage()
    accountStorage.putIfAbsent(existingAccountId, existingAccount)
    balanceStorage.putIfAbsent(existingAccountId, 10000)
    service = AccountServiceImpl(accountStorage, balanceStorage)
  }

  "AccountService#createAccount" should {
    "create a vaild account and put it into storage" when {
      "all specified parameters are correct" in {
        val result = service.createAccount(newAccountId, "New account", 10000, None)

        result shouldEqual Right(newAccountPrototype)
        accountStorage.get(newAccountId) shouldEqual Some(newAccountPrototype)
        balanceStorage.get(newAccountId) shouldEqual Some(10000)
      }

      "a maximum limit is greater than initial balance" in {
        val expectedAccount = newAccountPrototype.copy(maxLimit = Some(20000))
        val result = service.createAccount(newAccountId, "New account", 10000, Some(20000))

        result shouldEqual Right(expectedAccount)
        accountStorage.get(newAccountId) shouldEqual Some(expectedAccount)
        balanceStorage.get(newAccountId) shouldEqual Some(10000)
      }
    }

    "return an error" when {
      "account with specified id already exists" in {
        val result = service.createAccount(existingAccountId, "New account", 10000, None)

        result shouldEqual Left(List(ValidationError("Account with id 123-456-7890 already exists")))
      }
      "initial balance is negative" in {
        val result = service.createAccount(newAccountId, "New account", -10000, None)

        result shouldEqual Left(List(ValidationError("Initial account balance must be non-negative")))
      }
      "a maximum limit is less than initial balance" in {
        val result = service.createAccount(newAccountId, "New account", 20000, Some(10000))

        result shouldEqual Left(List(ValidationError("Maximum balance limit must be no less than initial balance")))
      }
    }
  }

  "AccountService#getAccount" should {
    "retrieve an account" when {
      "it exists in the storage" in {
        val result = service.getAccount(existingAccountId)

        result shouldEqual Right(existingAccount)
      }
    }
    "return an error" when {
      "account does not exist" in {
        val result = service.getAccount(newAccountId)

        result shouldEqual Left(List(ValidationError("Account with id 987-654-3210 does not exist")))
      }
    }
  }

  "AccountService#balance" should {
    "retrieve account balance" when {
      "account exists in the storage" in {
        val result = service.balance(existingAccountId)

        result shouldEqual Right(Balance(10000))
      }
    }
    "return an error" when {
      "account does not exist" in {
        val result = service.balance(newAccountId)

        result shouldEqual Left(List(ValidationError("Account with id 987-654-3210 does not exist")))
      }
    }
  }
}
