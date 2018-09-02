package com.github.alextokarew.moneytransfer.domain

/**
  * Money transfer operation between two accounts.
  *
  * @param id unique identifier of the operation
  * @param from id of the source account. It's balance must be greater than or equal to the amount
  * @param to id of the destination account
  * @param amount the amount of money to transfer. Must be strictly positive.
  * @param timestamp the UTC timestamp of the operation
  * @param comment an optional description of the operation
  */
case class Transfer(id: String, from: AccountId, to: AccountId, amount: BigInt, timestamp: Long, comment: Option[String])
