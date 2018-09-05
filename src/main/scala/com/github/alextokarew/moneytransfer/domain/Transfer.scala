package com.github.alextokarew.moneytransfer.domain

import com.github.alextokarew.moneytransfer.validation.ValidationError

/**
  * A request for money transfer operation
  *
  * @param from id of the source account. It's balance must be greater than or equal to the amount
  * @param to id of the destination account
  * @param amount the amount of money to transfer. Must be strictly positive.
  * @param comment an optional description of the operation
  * @param token unique externally generated token for this operation. It is needed for idempotency,
  *              i.e. one token corresponds to only one actual operation.
  */
case class TransferRequest(from: AccountId, to: AccountId, amount: BigInt, comment: Option[String], token: String)

/**
  * Money transfer operation between two accounts.
  *
  * @param id unique identifier of the operation
  * @param request an incoming request for this transfer
  * @param status current status of the transfer
  * @param created Unix timestamp of creation time
  * @param updated Unit timestamp of the last update
  */
case class Transfer(id: Long, request: TransferRequest, status: TransferStatus, created: Long, updated: Long)

/**
  * Status of transfer operation.
  */
sealed trait TransferStatus

/**
  * An operation is being processed now.
  */
case object Processing extends TransferStatus

/**
  * An operaion was successfully completed.
  */
case object Succeded extends TransferStatus

/**
  * An operation was failed for some reason.
  * @param errors a list of errors describing why this operation was failed.
  */
case class Failed(errors: List[ValidationError]) extends TransferStatus