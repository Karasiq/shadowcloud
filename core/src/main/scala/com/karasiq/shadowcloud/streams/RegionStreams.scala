package com.karasiq.shadowcloud.streams

import java.io.FileNotFoundException

import akka.NotUsed
import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream.FlowShape
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, ZipWith}
import akka.util.Timeout
import com.karasiq.shadowcloud.actors.messages.RegionEnvelope
import com.karasiq.shadowcloud.actors.{ChunkIODispatcher, RegionDispatcher}
import com.karasiq.shadowcloud.config.ParallelismConfig
import com.karasiq.shadowcloud.index.diffs.{FileVersions, FolderIndexDiff}
import com.karasiq.shadowcloud.index.{Chunk, File, Path}
import com.karasiq.shadowcloud.utils.Utils

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

object RegionStreams {
  def apply(regionSupervisor: ActorRef, parallelism: ParallelismConfig)
           (implicit ec: ExecutionContext, timeout: Timeout = Timeout(5 minutes)): RegionStreams = {
    new RegionStreams(regionSupervisor, parallelism)
  }
}

class RegionStreams(val regionSupervisor: ActorRef, val parallelism: ParallelismConfig)
                   (implicit ec: ExecutionContext, timeout: Timeout = Timeout(5 minutes)) {
  type ChunkFlow = Flow[(String, Chunk), Chunk, NotUsed]

  val writeChunks: ChunkFlow = Flow[(String, Chunk)]
    .mapAsync(parallelism.write) { case (regionId, chunk) ⇒
      regionSupervisor ? RegionEnvelope(regionId, RegionDispatcher.WriteChunk(chunk))
    }
    .map {
      case ChunkIODispatcher.WriteChunk.Success(_, chunk) ⇒
        chunk

      case ChunkIODispatcher.WriteChunk.Failure(_, error) ⇒
        throw error
    }

  val readChunks: ChunkFlow = Flow[(String, Chunk)]
    .mapAsync(parallelism.read) { case (regionId, chunk) ⇒
      regionSupervisor ? RegionEnvelope(regionId, RegionDispatcher.ReadChunk(chunk))
    }
    .map {
      case ChunkIODispatcher.ReadChunk.Success(_, chunk) ⇒
        chunk

      case ChunkIODispatcher.ReadChunk.Failure(_, error) ⇒
        throw error
    }

  val findFiles: Flow[(String, Path), (Path, Set[File]), NotUsed] = Flow[(String, Path)]
    .mapAsync(parallelism.read) { case (regionId, path) ⇒
      regionSupervisor ? RegionEnvelope(regionId, RegionDispatcher.GetFiles(path))
    }
    .map {
      case RegionDispatcher.GetFiles.Success(path, files) ⇒
        path → files

      case RegionDispatcher.GetFiles.Failure(path, _: FileNotFoundException) ⇒
        path → Set.empty[File]

      case RegionDispatcher.GetFiles.Failure(_, error) ⇒
        throw error
    }

  val findFile: Flow[(String, Path), File, NotUsed] = findFiles
    .map(_._2)
    .filter(_.nonEmpty)
    .map(FileVersions.mostRecent)

  val addFile: Flow[(String, Path, FileIndexer.Result), File, NotUsed] = {
    val graph = GraphDSL.create() { implicit builder ⇒
      import GraphDSL.Implicits._

      val input = builder.add(Broadcast[(String, Path, FileIndexer.Result)](2))
      val withFiles = builder.add(ZipWith((input: (String, Path, FileIndexer.Result), files: (Path, Set[File])) ⇒ (input, files)))
      input.out(0) ~> withFiles.in0
      input.out(1).map(kv ⇒ (kv._1, kv._2)) ~> findFiles ~> withFiles.in1
      FlowShape(input.in, withFiles.out)
    }

    Flow[(String, Path, FileIndexer.Result)]
      .via(graph)
      .map { case ((regionId, path, result), (path1, files)) ⇒
        require(path == path1)
        val newFile = if (files.nonEmpty) {
          val last = FileVersions.mostRecent(files)
          last.copy(lastModified = Utils.timestamp, checksum = result.checksum, chunks = result.chunks)
        } else {
          File(path, Utils.timestamp, Utils.timestamp, result.checksum, result.chunks)
        }
        if (!files.contains(newFile)) {
          regionSupervisor ! RegionEnvelope(regionId, RegionDispatcher.WriteIndex(FolderIndexDiff.createFiles(newFile)))
        }
        newFile
      }
  }
}
