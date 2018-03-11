package me.hongchao.bitcoin4s

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Materializer}
import com.typesafe.scalalogging.StrictLogging
import me.hongchao.bitcoin4s.external.HttpSender
import me.hongchao.bitcoin4s.external.blockcypher.Api

trait Boot extends StrictLogging {
  implicit val system: ActorSystem = ActorSystem("bitcoin4s")

  implicit val materializer: Materializer = ActorMaterializer(ActorMaterializerSettings(system))

  import system.dispatcher

  sys.addShutdownHook {
    logger.info("Bitcoin4s shutting down.")
  }

  val httpSender = new HttpSender()
  val blockcypherApi = new Api(httpSender)

  val routes = new Routes(blockcypherApi)
}