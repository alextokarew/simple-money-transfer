package com.github.alextokarew.moneytransfer.web.request

import com.github.alextokarew.moneytransfer.domain.AccountId

/**
 * Class for deserializing account create request body. It's arguments are the same as
 * [[com.github.alextokarew.moneytransfer.service.AccountService.createAccount]]
 */
case class CreateAccountRequest(id: AccountId, description: String, initialBalance: BigInt, maxLimit: Option[BigInt])
