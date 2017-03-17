package com.karasiq.shadowcloud.shell

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.karasiq.shadowcloud.actors.RegionSupervisor
import com.karasiq.shadowcloud.config.AppConfig
import com.karasiq.shadowcloud.streams.{ChunkProcessing, FileStreams, RegionOps, RegionStreams}

import scala.concurrent.duration._
import scala.language.postfixOps

private[shell] object ShellContext {
  def apply(): ShellContext = {
    new ShellContext()
  }
}

private[shell] final class ShellContext {
  implicit val actorSystem = ActorSystem("shadowcloud-shell")
  implicit val actorMaterializer = ActorMaterializer()
  implicit val defaultTimeout = Timeout(15 seconds)
  implicit val executionContext = actorSystem.dispatcher
  val config = AppConfig(actorSystem)
  val chunkProcessing = ChunkProcessing(config)
  val regionSupervisor = actorSystem.actorOf(RegionSupervisor.props, "regions")
  val regionOps = RegionOps(regionSupervisor)
  val regionStreams = RegionStreams(regionSupervisor, config.parallelism)
  val fileStreams = FileStreams(regionStreams, chunkProcessing)
}
