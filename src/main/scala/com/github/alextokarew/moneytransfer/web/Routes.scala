package com.github.alextokarew.moneytransfer.web

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.github.alextokarew.moneytransfer.domain.{ AccountId, TransferRequest }
import com.github.alextokarew.moneytransfer.service.{ AccountService, TransferService }
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
            })
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

  def transfer(service: TransferService): Route = pathPrefix("transfer") {
    concat(
      get {
        path(LongNumber) { id =>
          completeValid(StatusCodes.NotFound) {
            service.getTransfer(id)
          }
        }
      },
      post {
        pathEndOrSingleSlash {
          entity(as[TransferRequest]) { request =>
            completeValid(StatusCodes.BadRequest) {
              service.createTransfer(request)
            }
          }
        }
      })
  }

  def all(accountService: AccountService, transferService: TransferService): Route = concat(
    account(accountService),
    transfer(transferService))

}
