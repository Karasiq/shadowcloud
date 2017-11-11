package com.karasiq.shadowcloud.ops.region

import scala.concurrent.Future

import akka.stream._
import akka.stream.scaladsl.Source

import com.karasiq.shadowcloud.config.SCConfig
import com.karasiq.shadowcloud.model.{Chunk, RegionId}
import com.karasiq.shadowcloud.streams.region.RegionRepairStream

private[shadowcloud] object BackgroundOps {
  def apply(config: SCConfig, regionOps: RegionOps)(implicit mat: Materializer): BackgroundOps = {
    new BackgroundOps(config, regionOps)
  }
}

private[shadowcloud] final class BackgroundOps(config: SCConfig, regionOps: RegionOps)(implicit mat: Materializer) {
  private[this] val repairStream = RegionRepairStream(config.parallelism, regionOps)
    .withAttributes(Attributes.name("regionRepair") and ActorAttributes.supervisionStrategy(Supervision.resumingDecider))

  def repair(regionId: RegionId, strategy: RegionRepairStream.Strategy, chunks: Seq[Chunk] = Nil): Future[Seq[Chunk]] = { // TODO: Queue
    val request = RegionRepairStream.Request(regionId, strategy, chunks)
    Source.single(request)
      .to(repairStream)
      .mapMaterializedValue(_ ⇒ request.result.future)
      .named("regionRepairStream")
      .run()
  }
}
