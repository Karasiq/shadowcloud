package com.karasiq.shadowcloud.streams.region

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

import akka.NotUsed
import akka.actor.ActorRef
import akka.stream.scaladsl.{Flow, Source}

import com.karasiq.shadowcloud.config.{ParallelismConfig, TimeoutsConfig}
import com.karasiq.shadowcloud.index.{Chunk, File, Path}
import com.karasiq.shadowcloud.index.diffs.FileVersions
import com.karasiq.shadowcloud.ops.region.RegionOps
import com.karasiq.shadowcloud.streams.file.FileIndexer

object RegionStreams {
  def apply(regionSupervisor: ActorRef, parallelism: ParallelismConfig, timeouts: TimeoutsConfig)
           (implicit ec: ExecutionContext): RegionStreams = {
    new RegionStreams(regionSupervisor, parallelism, timeouts)
  }
}

//noinspection TypeAnnotation
// RegionOps wrapped in flows
final class RegionStreams(regionSupervisor: ActorRef, parallelism: ParallelismConfig, timeouts: TimeoutsConfig)
                         (implicit ec: ExecutionContext) {

  private[this] val regionOps = RegionOps(regionSupervisor, timeouts)

  val writeChunks = Flow[(String, Chunk)]
    .mapAsync(parallelism.write) { case (regionId, chunk) ⇒ regionOps.writeChunk(regionId, chunk) }
    .named("writeChunks")

  val readChunks = Flow[(String, Chunk)]
    .mapAsync(parallelism.read) { case (regionId, chunk) ⇒ regionOps.readChunk(regionId, chunk) }
    .named("readChunks")

  val findFiles = Flow[(String, Path)]
    .mapAsync(parallelism.query) { case (regionId, path) ⇒
      regionOps.getFiles(regionId, path)
        .map((path, _))
        .recover { case _ ⇒ (path, Set.empty[File]) } // TODO: Region exceptions
    }
    .named("findFiles")

  val findFile = findFiles
    .map(e ⇒ FileVersions.mostRecent(e._2))
    .named("findFile")

  val getFolder = Flow[(String, Path)]
    .mapAsync(parallelism.query) { case (regionId, path) ⇒
      regionOps.getFolder(regionId, path)
    }
    .named("getFolder")

  val createFile: Flow[(String, Path, FileIndexer.Result), File, NotUsed] = {
    Flow[(String, Path, FileIndexer.Result)]
      .flatMapConcat { case (regionId, path, result) ⇒
        val future = regionOps.createFile(regionId, File.create(path, result.checksum, result.chunks))
        Source.fromFuture(future)
      }
      .named("createFile")
  }
}
