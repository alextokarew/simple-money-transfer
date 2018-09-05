package com.github.alextokarew.moneytransfer

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.github.alextokarew.moneytransfer.domain.{Account, AccountId}
import com.github.alextokarew.moneytransfer.service.AccountServiceImpl
import com.github.alextokarew.moneytransfer.storage.InMemoryStorage
import com.github.alextokarew.moneytransfer.web.Routes
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging

import scala.io.StdIn
import scala.util.Try

object Application extends LazyLogging {

  def main(args: Array[String]): Unit = {
    logger.info("Starting an application")

    val config: Config = ConfigFactory.load()

    implicit val system: ActorSystem = ActorSystem("helloAkkaHttpServer")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    import system.dispatcher

    val interface = config.getString("service.http.interface")
    val port = Try(args(0).toInt).toOption.getOrElse(config.getInt("service.http.port"))

    val accountStorage = new InMemoryStorage[AccountId, Account]()
    val balanceStorage = new InMemoryStorage[AccountId, BigInt]()

    val accountService = new AccountServiceImpl(accountStorage, balanceStorage)

    val bindingFuture = Http().bindAndHandle(Routes.all(accountService), interface, port)

    println(s"Server is running at $interface:$port, press ENTER to stop")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate())
  }

}
