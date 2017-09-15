package com.karasiq.shadowcloud.streams.region

import scala.concurrent.Promise
import scala.util.Failure

import akka.NotUsed
import akka.stream.{ActorAttributes, Attributes, Supervision}
import akka.stream.scaladsl.{Flow, Sink, Source}

import com.karasiq.shadowcloud.config.ParallelismConfig
import com.karasiq.shadowcloud.model.{Chunk, RegionId}
import com.karasiq.shadowcloud.ops.region.RegionOps
import com.karasiq.shadowcloud.storage.replication.ChunkWriteAffinity
import com.karasiq.shadowcloud.storage.replication.ChunkStatusProvider.ChunkStatus
import RegionRepairStream.Strategy.{AutoAffinity, SetAffinity, TransformAffinity}

object RegionRepairStream {
  sealed trait Strategy
  object Strategy {
    case object AutoAffinity extends Strategy
    case class SetAffinity(newAffinity: ChunkWriteAffinity) extends Strategy
    case class TransformAffinity(newAffinity: ChunkStatus ⇒ Option[ChunkWriteAffinity]) extends Strategy
  }

  case class Request(regionId: RegionId, strategy: Strategy, chunks: Seq[Chunk] = Nil, result: Promise[Seq[Chunk]] = Promise())

  def apply(parallelism: ParallelismConfig, regionOps: RegionOps): Sink[Request, NotUsed] = {
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
          Source.fromFuture(regionOps.getChunkIndex(request.regionId)).mapConcat(_.chunks)
        }

        chunksSource
          .mapAsyncUnordered(parallelism.read)(regionOps.getChunkStatus(request.regionId, _))
          .flatMapConcat { status ⇒
            val newAffinity = createNewAffinity(status, request.strategy)
            Source.single(status)
              .filterNot(status ⇒ newAffinity.exists(_.isFinished(status)))
              .mapAsyncUnordered(parallelism.read)(status ⇒ regionOps.readChunk(request.regionId, status.chunk))
              .mapAsyncUnordered(parallelism.write)(chunk ⇒ regionOps.rewriteChunk(request.regionId, chunk, newAffinity))
              .map(_.withoutData)
              .log("region-repair-chunk", chunk ⇒ chunk.toString + " at " + request.regionId)
              .addAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
              .fold(Vector.empty[Chunk])(_ :+ _)
              .alsoTo(Sink.onComplete {
                case Failure(error) ⇒
                  request.result.tryFailure(error)

                case _ ⇒
                // Ignore
              })
              .alsoTo(Sink.foreach(request.result.success))
          }
      }
      .to(Sink.ignore)
      .addAttributes(Attributes.name("regionRepairStream"))
  }
}
