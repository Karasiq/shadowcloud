package com.karasiq.shadowcloud.streams

import scala.concurrent.Promise
import scala.util.Failure

import akka.NotUsed
import akka.stream.{ActorAttributes, Attributes, Supervision}
import akka.stream.scaladsl.{Flow, Sink, Source}

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.index.Chunk
import com.karasiq.shadowcloud.storage.replication.ChunkWriteAffinity
import com.karasiq.shadowcloud.storage.replication.ChunkStatusProvider.ChunkStatus
import com.karasiq.shadowcloud.storage.utils.IndexMerger
import com.karasiq.shadowcloud.storage.utils.IndexMerger.RegionKey
import com.karasiq.shadowcloud.streams.RegionRepairStream.Strategy.{AutoAffinity, SetAffinity, TransformAffinity}

object RegionRepairStream {
  sealed trait Strategy
  object Strategy {
    case object AutoAffinity extends Strategy
    case class SetAffinity(newAffinity: ChunkWriteAffinity) extends Strategy
    case class TransformAffinity(newAffinity: ChunkStatus ⇒ Option[ChunkWriteAffinity]) extends Strategy
  }

  case class Request(regionId: String, strategy: Strategy, chunks: Seq[Chunk] = Nil, result: Promise[Seq[Chunk]] = Promise())

  def apply(sc: ShadowCloudExtension): Sink[Request, NotUsed] = {
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

    val readParallelism = sc.config.parallelism.read
    val writeParallelism = sc.config.parallelism.write
    Flow[Request]
      .log("region-repair-request")
      .flatMapConcat { request ⇒
        val chunksSource: Source[Chunk, NotUsed] = if (request.chunks.nonEmpty) {
          Source(request.chunks.toVector)
        } else {
          Source.fromFuture(sc.ops.region.getIndex(request.regionId))
            .mapConcat { state ⇒
              val index = IndexMerger.restore(RegionKey.zero, state)
              index.chunks.chunks
            }
        }

        chunksSource
          .mapAsyncUnordered(readParallelism)(sc.ops.region.getChunkStatus(request.regionId, _))
          .flatMapConcat { status ⇒
            val newAffinity = createNewAffinity(status, request.strategy)
            Source.single(status)
              .filterNot(status ⇒ newAffinity.exists(_.isFinished(status)))
              .mapAsyncUnordered(readParallelism)(status ⇒ sc.ops.region.readChunk(request.regionId, status.chunk))
              .mapAsyncUnordered(writeParallelism)(chunk ⇒ sc.ops.region.rewriteChunk(request.regionId, chunk, newAffinity))
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
