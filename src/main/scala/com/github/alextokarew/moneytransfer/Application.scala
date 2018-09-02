package com.github.alextokarew.moneytransfer

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.github.alextokarew.moneytransfer.web.Routes
import com.typesafe.config.{ Config, ConfigFactory }

import scala.io.StdIn
import scala.util.Try

object Application {

  def main(args: Array[String]): Unit = {
    println("Starting an application")

    val config: Config = ConfigFactory.load()

    implicit val system: ActorSystem = ActorSystem("helloAkkaHttpServer")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    import system.dispatcher

    val interface = config.getString("service.http.interface")
    val port = Try(args(0).toInt).toOption.getOrElse(config.getInt("service.http.port"))

    val bindingFuture = Http().bindAndHandle(Routes.all(), interface, port)

    println(s"Server is running at $interface:$port, press ENTER to stop")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate())
  }

}
