package com.github.alextokarew.moneytransfer.web

import org.scalatest.{ Matchers, WordSpec }

class RoutesSpec extends WordSpec with Matchers {

  "Account routes" should {
    "create new account" in ???
    "return an error message if account can't be created" in ???
    "retrieve an existing account by id" in ???
    "return 404 status if account was not found" in ???
  }

  "Transfer routes" should {
    "Create a successful account transfer" in ???
    "Return an error message if the transfer can't be created" in ???
    "Retrieve an existing transfer by id" in ???
  }

}
