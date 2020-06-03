package com.karasiq.shadowcloud.streams.region

import akka.NotUsed
import akka.event.Logging
import akka.stream.scaladsl.{Flow, RestartFlow, Sink, Source}
import akka.stream.{ActorAttributes, Supervision}
import com.karasiq.shadowcloud.config.ParallelismConfig
import com.karasiq.shadowcloud.model.{Chunk, RegionId}
import com.karasiq.shadowcloud.ops.region.RegionOps
import com.karasiq.shadowcloud.storage.replication.ChunkStatusProvider.ChunkStatus
import com.karasiq.shadowcloud.storage.replication.ChunkWriteAffinity
import com.karasiq.shadowcloud.streams.region.RegionRepairStream.Strategy.{AutoAffinity, SetAffinity, TransformAffinity}
import com.karasiq.shadowcloud.streams.utils.{AkkaStreamUtils, ByteStreams}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Promise}

object RegionRepairStream {
  sealed trait Strategy
  object Strategy {
    case object AutoAffinity                                                                  extends Strategy
    final case class SetAffinity(newAffinity: ChunkWriteAffinity)                             extends Strategy
    final case class TransformAffinity(newAffinity: ChunkStatus ⇒ Option[ChunkWriteAffinity]) extends Strategy
  }

  final case class Request(regionId: RegionId, strategy: Strategy, chunks: Seq[Chunk] = Nil, result: Promise[Seq[Chunk]] = Promise())

  def apply(regionOps: RegionOps, parallelism: ParallelismConfig, bufferSize: Long)(implicit ec: ExecutionContext): Sink[Request, NotUsed] = {
    def createNewAffinity(status: ChunkStatus, strategy: Strategy): Option[ChunkWriteAffinity] = {
      strategy match {
        case AutoAffinity ⇒
          None

        case SetAffinity(newAffinity) ⇒
          Some(newAffinity)

        case TransformAffinity(newAffinityFunction) ⇒
          newAffinityFunction(status)
      }
    }

    Flow[Request]
      .log("region-repair-request")
      .flatMapConcat { request ⇒
        val chunksSource: Source[Chunk, NotUsed] = if (request.chunks.nonEmpty) {
          Source(request.chunks.toList)
        } else {
          Source
            .future(regionOps.getChunkIndex(request.regionId))
            .mapConcat(_.chunks.toVector.sortBy(-_.checksum.encSize))
        }

        chunksSource
          .via(RestartFlow.onFailuresWithBackoff(500 millis, 10 seconds, 0.2, 20) { () ⇒
            Flow[Chunk].mapAsyncUnordered(parallelism.query)(regionOps.getChunkStatus(request.regionId, _))
          })
          .via(
            Flow[ChunkStatus]
              .map(status ⇒ status → createNewAffinity(status, request.strategy))
              .filterNot { case (status, newAffinity) ⇒ newAffinity.exists(_.isFinished(status)) }
              .via(RestartFlow.onFailuresWithBackoff(500 millis, 10 seconds, 0.2, 20) { () ⇒
                Flow[(ChunkStatus, Option[ChunkWriteAffinity])].mapAsyncUnordered(parallelism.read) {
                  case (status, newAffinity) ⇒
                    regionOps.readChunkEncrypted(request.regionId, status.chunk).map(_ → newAffinity)
                }
              })
              .log("region-repair-read", chunk ⇒ s"${chunk._1.hashString} at ${request.regionId}")
              .via(ByteStreams.bufferT(_._1.data.encrypted.length, bufferSize))
              .via(RestartFlow.onFailuresWithBackoff(500 millis, 10 seconds, 0.2, 20) { () ⇒
                Flow[(Chunk, Option[ChunkWriteAffinity])].mapAsyncUnordered(parallelism.write) {
                  case (chunk, newAffinity) ⇒
                    regionOps.rewriteChunk(request.regionId, chunk, newAffinity)
                }
              })
              .log("region-repair-write", chunk ⇒ s"${chunk.hashString} at ${request.regionId}")
              .withAttributes(ActorAttributes.logLevels(onElement = Logging.InfoLevel))
              .map(_.withoutData)
          )
          .fold(Nil: Seq[Chunk])(_ :+ _)
          .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
          .alsoTo(AkkaStreamUtils.successPromiseOnFirst(request.result))
      }
      .recover { case _ ⇒ Nil }
      .to(Sink.ignore)
      .named("regionRepairStream")
  }
}
