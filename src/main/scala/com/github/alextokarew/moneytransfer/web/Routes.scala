package com.github.alextokarew.moneytransfer.web

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

object Routes {

  def account(): Route = path("account") {
    concat(
      get {
        ???
      },
      post {
        ???
      })
  }

  def transfer(): Route = path("transfer") {
    concat(
      get {
        ???
      },
      post {
        ???
      })
  }

  def all(): Route = concat(account(), transfer())

}
