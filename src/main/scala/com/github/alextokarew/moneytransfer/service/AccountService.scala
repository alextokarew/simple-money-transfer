package com.github.alextokarew.moneytransfer.service

import com.github.alextokarew.moneytransfer.domain.{Account, AccountId}
import com.github.alextokarew.moneytransfer.storage.Storage
import com.github.alextokarew.moneytransfer.validation.Validation._

trait AccountService {

  /**
    * Create a new account with specified initial balance.
    * @param id externally generated account id, must be unique
    * @param description account description
    * @param initialBalance initial account balance
    * @param maxLimit optional maximum balance limit
    * @return the result of account creation
    */
  def createAccount(id: AccountId, description: String, initialBalance: BigInt, maxLimit: Option[BigInt]): Valid[Account]


  /**
    * Updates specified fields of an existing account.
    * @param id id of the account to update
    * @param description description to update
    * @param maxLimit maximum balance limit to update
    * @param locked whether to lock this account or not
    * @return the result of account update and updated account instance
    */
  def updateAccount(id: AccountId, description: Option[String], maxLimit: Option[Option[BigInt]], locked: Option[Boolean]): Valid[Account]
}

class AccountServiceImpl(storage: Storage[AccountId, Account]) extends AccountService {

  private val existenceCheck = check[Account](a => !storage.exists(a.id), "Account with specified id already exists")
  private val initialBalanceCheck = check[Account](a => a.balance >= 0, "Initial account balance must be positive")
  private val maxLimitCheck = check[Account](a => a.maxLimit.fold(true)(_ >= a.balance), "Maximum balance limit must be no less than current balance")

  override def createAccount(id: AccountId, description: String, initialBalance: BigInt, maxLimit: Option[BigInt]): Valid[Account] = {
    val account = Account(id, description, initialBalance, maxLimit, locked = false)
    validate(account)(existenceCheck, initialBalanceCheck, maxLimitCheck).map { a =>
      storage.put(a.id, a)
    }
  }

  override def updateAccount(id: AccountId, description: Option[String], maxLimit: Option[Option[BigInt]], locked: Option[Boolean]): Valid[Account] = {
    validate(storage.get(id))(
      check(optAccount => optAccount.isDefined, s"Account with specified id was not found")
    ).flatMap { case Some(account) =>
      val updateAllFields = updateField(description, (a, d) => a.copy(description = d))
        .andThen(updateField(maxLimit, (a, ml) => a.copy(maxLimit = ml)))
        .andThen(updateField(locked, (a, l) => a.copy(locked = l)))


      validate(updateAllFields(account))(maxLimitCheck).map { a =>
        storage.put(a.id, a)
      }
    }
  }

  private def updateField[T](field: Option[T], doUpdate: (Account, T) => Account): Account => Account = { a: Account =>
    field.foldLeft(a)(doUpdate)
  }
}
