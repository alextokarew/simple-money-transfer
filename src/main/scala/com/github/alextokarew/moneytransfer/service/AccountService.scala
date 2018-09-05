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
    * @return newly created account or error list
    */
  def createAccount(id: AccountId, description: String, initialBalance: BigInt, maxLimit: Option[BigInt]): Valid[Account]

  /**
    * Retrieves account information by id
    * @param id account identifier
    * @return an existing account or error description
    */
  def getAccount(id: AccountId): Valid[Account]

  /**
    * Retrieves balance for specified account
    * @param id account identifier
    * @return current account balance if account exists or error description
    */
  def balance(id: AccountId): Valid[BigInt]
}

class AccountServiceImpl(accountStorage: Storage[AccountId, Account],
                         balancesStorage: Storage[AccountId, BigInt]) extends AccountService {

  override def createAccount(id: AccountId, description: String, initialBalance: BigInt, maxLimit: Option[BigInt]): Valid[Account] = {
    val account = Account(id, description, maxLimit)
    validate(account)(
      check(a => !accountStorage.exists(a.id), s"Account with id $id already exists"),
      check(_ => initialBalance >= 0, "Initial account balance must be positive"),
      check(a => a.maxLimit.fold(true)(_ >= initialBalance), "Maximum balance limit must be no less than initial balance")
    ).map { a =>
      balancesStorage.putIfAbsent(a.id, initialBalance)
      accountStorage.putIfAbsent(a.id, a)
    }
  }

  override def getAccount(id: AccountId): Valid[Account] = getById(id, accountStorage)

  override def balance(id: AccountId): Valid[BigInt] = getById(id, balancesStorage)

  private def getById[V](id: AccountId, storage: Storage[AccountId, V]): Valid[V] = {
    validate(storage.get(id))(
      check(_.isDefined, s"Account with id $id does not exist")
    ).map(_.get)
  }
}
