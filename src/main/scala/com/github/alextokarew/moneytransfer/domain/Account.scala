package com.github.alextokarew.moneytransfer.domain

/**
  * An account domain entity.
  * @param id account unique identifier
  * @param description account description
  * @param balance current balance, must be non-negative
  */
case class Account(id: AccountId, description: String, balance: BigInt)

/**
  * Value object for representing account id. This object can be serialized to and deserialized from String,
  * so it can help to reduce the whole indentation level.
  * @param value account id value. We represent it here as plain string for simplicity, but there can be more reasonable
  *              parts.
  */
case class AccountId(value: String)
