package com.github.alextokarew.moneytransfer.domain

/**
  * An account domain entity.
  * @param id account unique identifier
  * @param description account description
  * @param balance current balance, must be non-negative
  * @param maxLimit optional maximum balance limit, may be set for some legal purposes
  * @param locked is this account locked. Locked account doesn't allow any operations
  */
case class Account(id: AccountId, description: String, balance: BigInt, maxLimit: Option[BigInt], locked: Boolean)

/**
  * Value object for representing account id. This object can be serialized to and deserialized from String,
  * so it can help to reduce the whole indentation level.
  * @param value account id value. We represent it here as plain string for simplicity, but there can be more reasonable
  *              parts.
  */
case class AccountId(value: String)
