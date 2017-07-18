package com.karasiq.shadowcloud.actors

import scala.language.postfixOps

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, PossiblyHarmful, Props, Stash}
import akka.stream.{ActorAttributes, Attributes, Supervision}
import akka.stream.scaladsl.{Sink, Source}

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.index.Chunk
import com.karasiq.shadowcloud.storage.replication.ChunkWriteAffinity
import com.karasiq.shadowcloud.storage.utils.IndexMerger
import com.karasiq.shadowcloud.storage.utils.IndexMerger.RegionKey
import com.karasiq.shadowcloud.utils.Utils

object RegionRepairDispatcher {
  // Messages
  sealed trait Message
  case class Repair(newAffinity: ChunkWriteAffinity, chunks: Seq[Chunk] = Seq.empty) extends Message
  case object Repair extends MessageStatus[(ChunkWriteAffinity, Seq[Chunk]), Seq[Chunk]]

  // Internal messages
  private sealed trait InternalMessage extends Message with PossiblyHarmful
  private case class ChunkRepaired(chunk: Chunk) extends InternalMessage
  private case object RepairFinished extends InternalMessage

  // Events
  sealed trait Event

  // Props
  def props(regionId: String): Props = {
    Props(classOf[RegionRepairDispatcher], regionId)
  }
}

private final class RegionRepairDispatcher(regionId: String) extends Actor with ActorLogging with Stash {
  import RegionRepairDispatcher._
  private[this] val sc = ShadowCloud()

  def receiveRepairing(receiver: ActorRef, newAffinity: ChunkWriteAffinity, chunks: Seq[Chunk], result: Seq[Chunk]): Receive = {
    case ChunkRepaired(chunk) ⇒
      log.info("Chunk repaired: {}", chunk)
      context.become(receiveRepairing(receiver, newAffinity, chunks, result :+ chunk))

    case RepairFinished ⇒
      log.info("Chunk repair finished: [{}]", Utils.printChunkHashes(result))
      receiver ! Repair.Success((newAffinity, chunks), result)
      context.become(receive)
      unstashAll()

    case Repair(_, _) ⇒
      stash()
  }

  override def receive: Receive = {
    case Repair(newAffinity, chunks) ⇒
      def startRepair(chunksSource: Source[Chunk, NotUsed]): Unit = {
        import sc.implicits.materializer
        val parallelism = sc.config.parallelism.read
        val writeParallelism = sc.config.parallelism.write
        chunksSource
          .mapAsyncUnordered(parallelism)(chunk ⇒ sc.ops.region.getChunkStatus(regionId, chunk))
          .filterNot(newAffinity.isFinished)
          .mapAsyncUnordered(parallelism)(status ⇒ sc.ops.region.readChunk(regionId, status.chunk))
          .mapAsyncUnordered(writeParallelism)(chunk ⇒ sc.ops.region.rewriteChunk(regionId, chunk, Some(newAffinity)))
          .addAttributes(Attributes.name("regionRepairStream") and ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
          .map(chunk ⇒ ChunkRepaired(chunk.withoutData))
          .runWith(Sink.actorRef(self, RepairFinished))
        context.become(receiveRepairing(sender(), newAffinity, chunks, Nil))
      }

      val chunksSource: Source[Chunk, NotUsed] = if (chunks.nonEmpty) {
        Source(chunks.toVector)
      } else {
        Source.fromFuture(sc.ops.region.getIndex(regionId))
          .mapConcat { state ⇒
            val index = IndexMerger.restore(RegionKey.zero, state)
            index.chunks.chunks
          }
      }

      startRepair(chunksSource)
  }
}