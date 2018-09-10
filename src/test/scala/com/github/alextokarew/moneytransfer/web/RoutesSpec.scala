package com.github.alextokarew.moneytransfer.web

import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, StatusCodes }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.github.alextokarew.moneytransfer.domain._
import com.github.alextokarew.moneytransfer.service.{ AccountService, TransferService }
import com.github.alextokarew.moneytransfer.validation.ValidationError
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{ Matchers, WordSpec }

class RoutesSpec extends WordSpec with Matchers with MockitoSugar with ScalatestRouteTest {

  private val accountService = mock[AccountService]
  private val transferService = mock[TransferService]

  private val routes = Routes.all(accountService, transferService)

  "Account routes" should {

    "create new account" in {
      val body = """{"id":"123-456-7890","description": "Account", "initialBalance": 10000, "maxLimit": 20000}"""
      val expectedAccount = Account(AccountId("123-456-7890"), "Account", Some(20000))

      when(accountService.createAccount(AccountId("123-456-7890"), "Account", 10000, Some(20000)))
        .thenReturn(Right(expectedAccount))

      Post("/account", HttpEntity(ContentTypes.`application/json`, body)) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[String] shouldBe """{"id":"123-456-7890","description":"Account","maxLimit":20000}"""
      }
    }

    "return an error message if account can't be created" in {
      val body = """{"id":"123-456-7890","description": "Account", "initialBalance": 10000}"""
      when(accountService.createAccount(AccountId("123-456-7890"), "Account", 10000, None))
        .thenReturn(Left(List(ValidationError("Account already exist"), ValidationError("Some other error"))))

      Post("/account", HttpEntity(ContentTypes.`application/json`, body)) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
        contentType shouldBe ContentTypes.`application/json`
        responseAs[String] shouldBe """[{"errorMsg":"Account already exist"},{"errorMsg":"Some other error"}]"""
      }
    }

    "retrieve an existing account by id" in {
      when(accountService.getAccount(AccountId("123-456-7890")))
        .thenReturn(Right(Account(AccountId("123-456-7890"), "Account", None)))

      Get("/account/123-456-7890") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[String] shouldBe """{"id":"123-456-7890","description":"Account"}"""
      }
    }

    "retrieve an account balance by id" in {
      when(accountService.balance(AccountId("123-456-7890"))).thenReturn(Right(Balance(15000)))

      Get("/account/123-456-7890/balance") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[String] shouldBe """{"amount":15000}"""
      }
    }

    "return 404 status if account was not found" in {
      when(accountService.getAccount(AccountId("987-654-3210")))
        .thenReturn(Left(List(ValidationError("Account was not found"))))

      Get("/account/987-654-3210") ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
        contentType shouldBe ContentTypes.`application/json`
        responseAs[String] shouldBe """[{"errorMsg":"Account was not found"}]"""
      }
    }
  }

  "Transfer routes" should {
    "Create a successful account transfer" in {
      val body = """{"from":"123-456-7890","to":"987-654-3210","amount":50000,"comment":"bribe","token":"token-1"}"""
      val request = TransferRequest(AccountId("123-456-7890"), AccountId("987-654-3210"), 50000, Some("bribe"), "token-1")
      when(transferService.createTransfer(request)).thenReturn(Right(Transfer(3L, request, Processing, 12345L, 12345L)))

      Post("/transfer", HttpEntity(ContentTypes.`application/json`, body)) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[String] shouldBe """{"request":{"amount":50000,"to":"987-654-3210","comment":"bribe","token":"token-1","from":"123-456-7890"},"id":3,"updated":12345,"status":"Processing","created":12345}"""
      }
    }

    "Return an error message if the transfer can't be created" in {
      val body = """{"from":"bad-account","to":"987-654-3210","amount":50000,"token":"token-2"}"""
      val request = TransferRequest(AccountId("bad-account"), AccountId("987-654-3210"), 50000, None, "token-2")
      when(transferService.createTransfer(request)).thenReturn(Left(List(ValidationError("Source account does not exist"))))

      Post("/transfer", HttpEntity(ContentTypes.`application/json`, body)) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
        contentType shouldBe ContentTypes.`application/json`
        responseAs[String] shouldBe """[{"errorMsg":"Source account does not exist"}]"""
      }
    }

    "Retrieve an existing transfer by id" in {
      val request = TransferRequest(AccountId("123-456-7890"), AccountId("987-654-3210"), 50000, Some("bribe"), "token-1")
      when(transferService.getTransfer(3L)).thenReturn(Right(Transfer(3L, request, Succeded, 12345L, 12345L)))

      Get("/transfer/3") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[String] shouldBe """{"request":{"amount":50000,"to":"987-654-3210","comment":"bribe","token":"token-1","from":"123-456-7890"},"id":3,"updated":12345,"status":"Succeded","created":12345}"""
      }
    }

    "return 404 status if transfer was not found" in {
      when(transferService.getTransfer(5L)).thenReturn(Left(List(ValidationError("Transfer was not found"))))

      Get("/transfer/5") ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
        contentType shouldBe ContentTypes.`application/json`
        responseAs[String] shouldBe """[{"errorMsg":"Transfer was not found"}]"""
      }
    }
  }

}
