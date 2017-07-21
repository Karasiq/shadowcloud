package com.karasiq.shadowcloud.streams

import scala.concurrent.Future

import akka.stream._
import akka.stream.scaladsl.Source

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.index.Chunk

private[shadowcloud] object BackgroundOps {
  def apply(sc: ShadowCloudExtension): BackgroundOps = {
    new BackgroundOps(sc)
  }
}

private[shadowcloud] final class BackgroundOps(sc: ShadowCloudExtension) {
  import sc.implicits._

  private[this] val repairQueue = Source
    .queue[RegionRepairStream.Request](sc.config.queues.regionRepair, OverflowStrategy.dropNew)
    .to(RegionRepairStream(sc))
    .addAttributes(Attributes.name("regionRepairQueue") and ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
    .run()

  def repair(regionId: String, strategy: RegionRepairStream.Strategy, chunks: Seq[Chunk] = Nil): Future[Seq[Chunk]] = {
    val request = RegionRepairStream.Request(regionId, strategy, chunks)
    repairQueue.offer(request).flatMap {
      case QueueOfferResult.Enqueued ⇒
        request.result.future

      case _ ⇒
        Future.failed(new IllegalArgumentException("Repair queue is full"))
    }
  }
}
