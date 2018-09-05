package com.github.alextokarew.moneytransfer

import java.time.Clock

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.github.alextokarew.moneytransfer.domain.{Account, AccountId, Transfer}
import com.github.alextokarew.moneytransfer.process.Processor
import com.github.alextokarew.moneytransfer.service.{AccountServiceImpl, TransferServiceImpl}
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

    val clock = Clock.systemUTC()

    val accountStorage = InMemoryStorage[AccountId, Account]()
    val balanceStorage = InMemoryStorage[AccountId, BigInt]()
    val tokenStorage = InMemoryStorage[String, Long]()
    val transferStorage = InMemoryStorage[Long, Transfer]()

    val processor = new Processor {
      override def enqueue(transfer: Transfer): Unit = {
        println("This is a stub! I promise to replace it with proper implementation as soon as possible!!!")
      }
    }

    val accountService = AccountServiceImpl(accountStorage, balanceStorage)
    val transferService = TransferServiceImpl(processor, accountStorage, tokenStorage, transferStorage, clock)

    val bindingFuture = Http().bindAndHandle(Routes.all(accountService, transferService), interface, port)

    println(s"Server is running at $interface:$port, press ENTER to stop")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate())
  }

}
