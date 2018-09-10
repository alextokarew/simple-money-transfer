package com.github.alextokarew.moneytransfer.domain

import java.time.temporal.TemporalAmount

/**
 * An account domain entity.
 *
 * @param id account unique identifier
 * @param description account description
 * @param maxLimit optional maximum balance limit, may be set for some legal purposes
 */
case class Account(id: AccountId, description: String, maxLimit: Option[BigInt])

/**
 * Value object for representing account id. This object can be serialized to and deserialized from String,
 * so it can help to reduce the whole indentation level.
 * @param value account id value. We represent it here as plain string for simplicity, but there can be more reasonable
 *              parts.
 */
case class AccountId(value: String)

/**
 * Value object that represents account balance.
 * @param amount current amount of the accout balance, must be non-negative
 */
case class Balance(amount: BigInt)