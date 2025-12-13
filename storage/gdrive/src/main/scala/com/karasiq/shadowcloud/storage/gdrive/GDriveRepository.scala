package com.karasiq.shadowcloud.storage.gdrive

import java.io.InputStream

import akka.NotUsed
import akka.stream.ActorAttributes.Dispatcher
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, StreamConverters}
import akka.stream.{ActorAttributes, Supervision}
import com.google.api.client.http.FileContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.karasiq.common.memory.SizeUnit
import com.karasiq.shadowcloud.exceptions.StorageException
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.repository.PathTreeRepository
import com.karasiq.shadowcloud.storage.utils.StorageUtils
import com.karasiq.shadowcloud.streams.utils.AkkaStreamUtils

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

private[gdrive] object GDriveRepository {
  def apply(driveService: Drive)(implicit ec: ExecutionContext): GDriveRepository = {
    new GDriveRepository(driveService)
  }
}

/**
 * GDrive repository implementation using official Google Drive API v3.
 *
 * Features:
 * - Akka Streams-based file operations with backpressure support
 * - Efficient folder hierarchy caching with concurrent updates
 * - Proper error handling and timeout management
 * - Type-safe path representation
 */
private[gdrive] class GDriveRepository(driveService: Drive)(implicit ec: ExecutionContext) extends PathTreeRepository {

  // Cache folder IDs to avoid repeated API calls during traversal
  private[this] val folderIdCache: TrieMap[Path, String] = TrieMap.empty
  private[this] val GDRIVE_MIME_FOLDER = "application/vnd.google-apps.folder"
  private[this] val GDRIVE_ROOT = "root"

  /**
   * List all files/folders under a given path.
   *
   * @param fromPath starting path for traversal
   * @return source of all subpaths
   */
  def subKeys(fromPath: Path) = {
    Source
      .lazyFuture(() ⇒ getFolderId(fromPath))
      .flatMapConcat { parentId ⇒
        Source.unfold(Option(parentId)) {
          case Some(folderId) ⇒
            Try {
              val query = s"'$folderId' in parents and trashed=false"
              val files = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name, mimeType)")
                .setPageSize(1000L)
                .execute()
                .getFiles

              if (files != null && !files.isEmpty) {
                val (folders, results) = files.partition(_.getMimeType == GDRIVE_MIME_FOLDER)
                val nextFolderId = folders.headOption.map(_.getId)
                val filePaths = results.map(f ⇒ fromPath / f.getName)
                (nextFolderId, filePaths)
              } else {
                (None, Seq.empty)
              }
            }.toOption.getOrElse((None, Seq.empty))
          case None ⇒ None
        }
      }
      .mapConcat(identity)
      .via(StorageUtils.wrapStream(fromPath))
      .withAttributes(defaultAttributes)
      .named("gdriveSubKeys")
  }

  /**
   * Read file content as a stream.
   *
   * @param key file path
   * @return source of file data bytes
   */
  def read(key: Path) = {
    val idsSource = Source
      .lazyFuture(() ⇒ getFileId(key))
      .withAttributes(defaultAttributes)

    idsSource
      .flatMapConcat { fileId ⇒
        val inputStream = Try {
          driveService.files().get(fileId)
            .executeMediaAsInputStream()
        }.recover {
          case _ ⇒ throw StorageException.NotFound(key)
        }.get

        StreamConverters
          .fromInputStream(() ⇒ inputStream, SizeUnit.MB.intValue)
          .mapMaterializedValue(StorageUtils.wrapAkkaIOFuture(key, _))
      }
      .mapMaterializedValue(fs ⇒ StorageUtils.wrapFuture(key, fs.map(StorageUtils.foldIOResults)))
      .withAttributes(fileStreamAttributes)
      .named("gdriveRead")
  }

  /**
   * Write file content from a stream.
   *
   * @param path target file path
   * @return sink that accepts data bytes
   */
  def write(path: Path) = {
    def createFileInFolder(parentId: String, fileName: String, mimeType: String = "application/octet-stream"): String = {
      val fileMetadata = new File()
        .setName(fileName)
        .setMimeType(mimeType)
        .setParents(java.util.Collections.singletonList(parentId))

      driveService.files().create(fileMetadata)
        .setFields("id")
        .execute()
        .getId
    }

    def uploadContent(fileId: String, inputStream: InputStream): Long = {
      val fileContent = new FileContent("application/octet-stream", new java.io.File("/dev/null")) {
        override def getInputStream: InputStream = inputStream
      }

      val file = driveService.files().update(fileId, null, fileContent)
        .setFields("size")
        .execute()

      Option(file.getSize).map(_.toLong).getOrElse(0L)
    }

    Flow[Data]
      .via(AkkaStreamUtils.extractUpstream)
      .mapAsyncUnordered(1) { stream ⇒
        for {
          parentId ← getFolderId(path.parent)
          fileExists ← isFileExists(path)
          _ = if (fileExists) throw StorageException.AlreadyExists(path)
        } yield (stream, parentId, path.name)
      }
      .flatMapConcat { case (stream, parentId, fileName) ⇒
        stream.via(
          AkkaStreamUtils.writeInputStream(
            { inputStream ⇒
              val result = Try {
                val fileId = createFileInFolder(parentId, fileName)
                val written = uploadContent(fileId, inputStream)
                resetPathCache(path)
                written
              }
              Source.future(Future.fromTry(result))
            },
            Dispatcher(GDriveDispatchers.fileDispatcherId)
          )
        )
      }
      .map(written ⇒ StorageIOResult.Success(path, written))
      .withAttributes(fileStreamAttributes)
      .withAttributes(ActorAttributes.supervisionStrategy(Supervision.stoppingDecider))
      .via(StorageUtils.wrapStream(path))
      .toMat(Sink.head)(Keep.right)
      .named("gdriveWrite")
  }

  /**
   * Delete files at given paths.
   *
   * @return sink that accepts paths to delete
   */
  def delete = {
    Flow[Path]
      .mapAsyncUnordered(2) { path ⇒
        for {
          fileId ← getFileId(path)
          _ = Try(driveService.files().delete(fileId).execute()).isSuccess
        } yield path
        
        resetPathCache(path)
        StorageUtils.wrapCountFuture(path, Future.successful(1))
      }
      .idleTimeout(15 seconds)
      .via(StorageUtils.foldStream())
      .toMat(Sink.head)(Keep.right)
      .withAttributes(defaultAttributes)
  }

  /**
   * Get the Google Drive ID for a given path.
   * Falls back to traversing folder hierarchy if not cached.
   */
  private[this] def getFolderId(path: Path): Future[String] = Future {
    folderIdCache.getOrElseUpdate(
      path,
      traverseToFolder(path)
    )
  }

  /**
   * Get the Google Drive file ID by name within a parent folder.
   */
  private[this] def getFileId(path: Path): Future[String] = {
    for {
      parentId ← getFolderId(path.parent)
      fileId ← Future {
        val query = s"'$parentId' in parents and name='${escapeDriveQuery(path.name)}' and trashed=false"
        val files = driveService.files().list()
          .setQ(query)
          .setSpaces("drive")
          .setFields("files(id)")
          .setPageSize(1L)
          .execute()
          .getFiles

        files.headOption
          .map(_.getId)
          .getOrElse(throw StorageException.NotFound(path))
      }
    } yield fileId
  }

  /**
   * Check if a file exists at the given path.
   */
  private[this] def isFileExists(path: Path): Future[Boolean] = {
    getFileId(path)
      .map(_ ⇒ true)
      .recover { case _ ⇒ false }
  }

  /**
   * Traverse the folder hierarchy and create missing folders.
   */
  private[this] def traverseToFolder(path: Path): String = {
    var currentId = GDRIVE_ROOT

    for (folderName ← path.nodes) {
      val query = s"'$currentId' in parents and name='${escapeDriveQuery(folderName)}' and mimeType='$GDRIVE_MIME_FOLDER' and trashed=false"
      val existingFolder = Try {
        driveService.files().list()
          .setQ(query)
          .setSpaces("drive")
          .setFields("files(id)")
          .setPageSize(1L)
          .execute()
          .getFiles
          .headOption
          .map(_.getId)
      }.toOption.flatten

      currentId = existingFolder.getOrElse {
        // Create folder if it doesn't exist
        val newFolder = new File()
          .setName(folderName)
          .setMimeType(GDRIVE_MIME_FOLDER)
          .setParents(java.util.Collections.singletonList(currentId))

        driveService.files().create(newFolder)
          .setFields("id")
          .execute()
          .getId
      }
    }

    currentId
  }

  /**
   * Escape special characters in folder names for Google Drive queries.
   */
  private[this] def escapeDriveQuery(s: String): String = {
    s.replace("\\", "\\\\")
      .replace("'", "\\'")
  }

  /**
   * Reset cache entries for a path and its parents.
   */
  private[this] def resetPathCache(path: Path): Unit = {
    folderIdCache.remove(path)
    folderIdCache.remove(path.parent)
  }

  private[this] def defaultAttributes = {
    ActorAttributes.dispatcher(GDriveDispatchers.apiDispatcherId)
  }

  private[this] def fileStreamAttributes = {
    ActorAttributes.dispatcher(GDriveDispatchers.fileDispatcherId)
  }
}
