package com.github.alextokarew.moneytransfer.web

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.github.alextokarew.moneytransfer.domain.AccountId
import com.github.alextokarew.moneytransfer.service.AccountService
import com.github.alextokarew.moneytransfer.validation.Validation.Valid
import com.github.alextokarew.moneytransfer.web.request.CreateAccountRequest
import spray.json.RootJsonFormat

object Routes extends JsonSupport {

  private def completeValid[E: RootJsonFormat](errorStatus: StatusCode)(body: => Valid[E]) = complete {
    body match {
      case Right(entity) => entity
      case Left(errors) => errorStatus -> errors
    }
  }

  def account(service: AccountService): Route = pathPrefix("account") {
    concat(
      get {
        pathPrefix(Segment) { accountIdString =>
          val accountId = AccountId(accountIdString)
          concat(
            pathEndOrSingleSlash {
              completeValid(StatusCodes.NotFound) {
                service.getAccount(accountId)
              }
            },
            pathPrefix("balance") {
              pathEndOrSingleSlash {
                completeValid(StatusCodes.NotFound) {
                  service.balance(accountId)
                }
              }
            }
          )
        }
      },
      post {
        pathEndOrSingleSlash {
          entity(as[CreateAccountRequest]) { req =>
            completeValid(StatusCodes.BadRequest) {
              service.createAccount(req.id, req.description, req.initialBalance, req.maxLimit)
            }
          }
        }
      })
  }

  def transfer(): Route = pathPrefix("transfer") {
    concat(
      get {
        ???
      },
      post {
        ???
      })
  }

  def all(accountService: AccountService): Route = concat(
    account(accountService),
    transfer()
  )

}
