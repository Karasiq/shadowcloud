package com.karasiq.shadowcloud.streams

import java.io.FileNotFoundException

import akka.NotUsed
import akka.actor.ActorRef
import akka.stream.FlowShape
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, ZipWith}
import akka.util.Timeout
import com.karasiq.shadowcloud.actors.RegionDispatcher
import com.karasiq.shadowcloud.actors.messages.RegionEnvelope
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

final class RegionStreams(val regionSupervisor: ActorRef, val parallelism: ParallelismConfig)
                   (implicit ec: ExecutionContext, timeout: Timeout) {
  private[this] val regionOps = RegionOps(regionSupervisor)

  val writeChunks = Flow[(String, Chunk)].mapAsync(parallelism.write) { case (regionId, chunk) ⇒
    regionOps.writeChunk(regionId, chunk)
  }

  val readChunks = Flow[(String, Chunk)].mapAsync(parallelism.read) { case (regionId, chunk) ⇒
    regionOps.readChunk(regionId, chunk)
  }

  val findFiles = Flow[(String, Path)]
    .mapAsync(parallelism.read) { case (regionId, path) ⇒
      regionOps.getFiles(regionId, path)
        .map((path, _))
        .recover { case _: FileNotFoundException ⇒ (path, Set.empty[File]) }
    }

  val findFile = findFiles.map(e ⇒ FileVersions.mostRecent(e._2))

  val getFolder = Flow[(String, Path)].mapAsync(parallelism.read) { case (regionId, path) ⇒
    regionOps.getFolder(regionId, path)
  }

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
