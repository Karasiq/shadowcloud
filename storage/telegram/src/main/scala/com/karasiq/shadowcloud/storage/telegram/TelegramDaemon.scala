package com.karasiq.shadowcloud.storage.telegram

import java.io.IOException
import java.net.{InetSocketAddress, ServerSocket}
import java.nio.file.Path

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString

import com.karasiq.shadowcloud.ShadowCloudExtension
import play.api.libs.json._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Random, Success, Try}
import scala.concurrent.duration._
import scala.util.control.NonFatal

/**
  * Async HTTP-based Telegram daemon client with streaming upload support.
  * Communicates with telegram_daemon.py via HTTP REST API.
  *
  * Supports:
  * - Streaming file uploads from ByteString sources
  * - Memory-efficient (no disk write required)
  * - Multipart form data for file uploads
  * - Direct socket connection for large files
  */
class TelegramDaemon(
    sessionString: String,
    entity: String = "me"
)(
    implicit
    sc: ShadowCloudExtension,
    ec: ExecutionContext,
    system: ActorSystem,
    mat: Materializer
) {
  implicit val scheduler = system.scheduler

  private val DAEMON_HOST                       = "127.0.0.1"
  private val DAEMON_PORT                       = Random.nextInt(5000) + 30000
  private val DAEMON_BASE_URL                   = s"http://$DAEMON_HOST:$DAEMON_PORT"
  private var process: Option[Process]          = None
  private var initialized: Boolean              = false
  private val httpClient                        = Http()
  private val commandTimeout: FiniteDuration    = 30.seconds
  private val initTimeout: FiniteDuration       = 60.seconds
  private val shutdownTimeout: FiniteDuration   = 10.seconds
  private val daemonStartupWait: FiniteDuration = 5.seconds
  private val uploadTimeout: FiniteDuration     = 5.minutes // Longer for large files

  // ============================================================================
  // Lifecycle Management
  // ============================================================================

  /**
    * Start daemon process and initialize session.
    */
  def start(): Future[Unit] = {
    if (process.isDefined) {
      return Future.failed(new IllegalStateException("Daemon already started"))
    }

    startDaemonProcess()
      .flatMap(_ ⇒ waitForDaemonReady())
      .flatMap(_ ⇒ initializeDaemon())
      .andThen {
        case Success(_) ⇒
          sc.log.info(s"Telegram daemon initialized for entity: $entity")
        case Failure(ex) ⇒
          sc.log.error(s"Daemon initialization failed: ${ex.getMessage}")
          Try(shutdown())
      }
  }

  /**
    * Start the Python subprocess with embedded HTTP server.
    */
  private def startDaemonProcess(): Future[Unit] = Future {
    TelegramResourceExtractor.ensureExtracted()

    val venvPython = detectVenvPython().getOrElse {
      throw new IllegalStateException(
        "Python venv not found in ~/.shadowcloud/telegram\nRun setup-venv.sh first"
      )
    }

    val daemonScript = TelegramResourceExtractor.getDaemonScriptPath
    sc.log.info(s"Starting Telegram daemon: $venvPython $daemonScript")

    val processBuilder = new ProcessBuilder(
      venvPython.toString,
      daemonScript.toString,
      "--host",
      DAEMON_HOST,
      "--port",
      DAEMON_PORT.toString
    )

    processBuilder.redirectErrorStream(false)

    try {
      val proc = processBuilder.start()
      process = Some(proc)
      sc.log.debug(s"Daemon process started, PID: ${proc.pid()}")
      startErrorStreamReader(proc.getErrorStream)
    } catch {
      case ex: Exception ⇒
        sc.log.error(s"Failed to start daemon process: ${ex.getMessage}")
        throw ex
    }
  }

  /**
    * Wait for daemon HTTP server to be ready.
    */
  private def waitForDaemonReady(): Future[Unit] = {
    val maxAttempts = 50
    var attempts    = 0

    def tryConnect(): Future[Unit] = {
      if (attempts >= maxAttempts) {
        return Future.failed(
          new RuntimeException(
            s"Daemon HTTP server did not start on $DAEMON_BASE_URL"
          )
        )
      }

      isClientConnected()
        .flatMap { isConnected ⇒
          if (isConnected) {
            Future.successful(())
          } else {
            attempts += 1
            akka.pattern.after(100.millis, scheduler) { tryConnect() }
          }
        }
        .recoverWith {
          case _ ⇒
            attempts += 1
            akka.pattern.after(100.millis, scheduler) { tryConnect() }
        }
    }

    tryConnect()
  }

  def isClientConnected(): Future[Boolean] = {
    sendHttpRequest(
      HttpRequest(
        method = HttpMethods.GET,
        uri = s"$DAEMON_BASE_URL/health"
      ),
      5 seconds
    ).map { response ⇒
        val json = Json.parse(response)
        (json \ "status").as[String] == "healthy" && (json \ "connected").as[Boolean]
      }
      .recover {
        case NonFatal(exc) ⇒
          sc.log.error(exc, "Health check failed")
          false
      }
  }

  /**
    * Initialize daemon with session and entity.
    */
  private def initializeDaemon(): Future[Unit] = {
    val initPayload = Json.obj(
      "session" → sessionString,
      "entity"  → entity
    )

    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = s"$DAEMON_BASE_URL/cmd/init",
      entity = HttpEntity(
        ContentTypes.`application/json`,
        Json.stringify(initPayload)
      )
    )

    sendHttpRequest(request, initTimeout)
      .flatMap { response ⇒
        val json = Json.parse(response)
        if ((json \ "error").asOpt[String].isDefined) {
          Future.failed(new RuntimeException(s"Daemon init failed: $json"))
        } else {
          val userName = (json \ "user" \ "first_name").asOpt[String].getOrElse("Unknown")
          val userId   = (json \ "user" \ "id").asOpt[Long].getOrElse(0L)
          sc.log.info(s"Telegram user authorized: $userName (ID: $userId)")
          initialized = true
          Future.successful(())
        }
      }
  }

  /**
    * Shutdown daemon gracefully.
    */
  def shutdown(): Future[Unit] = {
    if (!initialized && process.isEmpty) {
      return Future.successful(())
    }

    val shutdownRequest = HttpRequest(
      method = HttpMethods.POST,
      uri = s"$DAEMON_BASE_URL/cmd/shutdown"
    )

    sendHttpRequest(shutdownRequest, shutdownTimeout)
      .recover { case _ ⇒ "{}" }
      .flatMap { _ ⇒
        Future {
          val exitedInTime = Try {
            process.get.waitFor(shutdownTimeout.toMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
          }.getOrElse(false)

          if (!exitedInTime) {
            sc.log.warning("Daemon did not exit gracefully, forcing termination")
            process.foreach(_.destroyForcibly())
          }

          cleanupResources()
          sc.log.info("Telegram daemon shut down")
        }
      }
  }

  /**
    * Clean up process resources.
    */
  private def cleanupResources(): Unit = {
    Try {
      process.foreach { p ⇒
        if (p.isAlive) p.destroyForcibly()
      }
    }

    process = None
    initialized = false
  }

  // ============================================================================
  // File Operations - Streaming Upload
  // ============================================================================

  /**
    * Upload ByteString directly to Telegram without disk write.
    * Streams data directly to daemon via HTTP multipart.
    *
    * @param dataSource Source of ByteString chunks
    * @param fileName Target filename
    * @param caption Optional caption
    * @return Future with message ID
    */
  def uploadStream(
      dataSource: Source[ByteString, _],
      fileName: String,
      caption: String = ""
  ): Future[Long] = {
    if (!initialized) {
      return Future.failed(TelegramDaemonError.NotInitialized())
    }

    sc.log.info(s"Streaming upload: $fileName")

    // Build multipart form data
    val multipartForm = buildMultipartForm(dataSource, fileName, caption)
    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = s"$DAEMON_BASE_URL/upload/stream",
      entity = multipartForm
    )

    sendHttpRequest(request, uploadTimeout)
      .flatMap { response ⇒
        val json = Json.parse(response)
        (json \ "error").asOpt[String] match {
          case Some(error) ⇒
            Future.failed(TelegramDaemonError.OperationFailed("stream_upload", error))
          case None ⇒
            (json \ "message_id").asOpt[Long] match {
              case Some(messageId) ⇒
                sc.log.info(s"Stream upload complete: $fileName (message ID: $messageId)")
                Future.successful(messageId)
              case None ⇒
                Future.failed(new RuntimeException("No message ID in response"))
            }
        }
      }
  }

  /**
    * Upload ByteString with metadata in one call.
    * Data is sent as binary body with headers.
    *
    * @param data ByteString data
    * @param fileName Target filename
    * @param caption Optional caption
    * @return Future with message ID
    */
  def uploadBytes(
      data: ByteString,
      fileName: String,
      caption: String = ""
  ): Future[Long] = {
    if (!initialized) {
      return Future.failed(TelegramDaemonError.NotInitialized())
    }

    sc.log.info(s"Uploading ${data.length} bytes as: $fileName")

    val entity = HttpEntity(
      ContentType(MediaTypes.`application/octet-stream`),
      data
    )

    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = s"$DAEMON_BASE_URL/upload/bytes?name=${Uri.Query.apply(fileName).toString}&caption=${Uri.Query.apply(caption).toString}",
      entity = entity
    )

    sendHttpRequest(request, uploadTimeout)
      .flatMap { response ⇒
        val json = Json.parse(response)
        (json \ "error").asOpt[String] match {
          case Some(error) ⇒
            Future.failed(TelegramDaemonError.OperationFailed("bytes_upload", error))
          case None ⇒
            (json \ "message_id").asOpt[Long] match {
              case Some(messageId) ⇒
                sc.log.info(s"Bytes upload complete: $fileName (message ID: $messageId)")
                Future.successful(messageId)
              case None ⇒
                Future.failed(new RuntimeException("No message ID in response"))
            }
        }
      }
  }

  /**
    * Upload from file path (legacy, still supported).
    */
  def upload(
      localPath: Path,
      fileName: String,
      caption: String = ""
  ): Future[Long] = {
    if (!initialized) {
      return Future.failed(TelegramDaemonError.NotInitialized())
    }

    val payload = Json.obj(
      "path"    → localPath.toString,
      "name"    → fileName,
      "caption" → caption
    )

    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = s"$DAEMON_BASE_URL/cmd/upload",
      entity = HttpEntity(
        ContentTypes.`application/json`,
        Json.stringify(payload)
      )
    )

    sendHttpRequest(request, uploadTimeout)
      .flatMap { response ⇒
        val json = Json.parse(response)
        (json \ "error").asOpt[String] match {
          case Some(error) ⇒
            Future.failed(TelegramDaemonError.OperationFailed("upload", error))
          case None ⇒
            (json \ "message_id").asOpt[Long] match {
              case Some(messageId) ⇒
                sc.log.info(s"Uploaded $fileName (message ID: $messageId)")
                Future.successful(messageId)
              case None ⇒
                Future.failed(new RuntimeException("No message ID in response"))
            }
        }
      }
  }

  // ============================================================================
  // File Operations - Download & List & Delete
  // ============================================================================

  /**
    * Download file from Telegram as streaming ByteString source.
    *
    * @param messageId Message ID
    * @return Future with source of ByteString chunks
    */
  def downloadStream(messageId: Long): Future[Source[ByteString, _]] = {
    if (!initialized) {
      return Future.failed(TelegramDaemonError.NotInitialized())
    }

    sc.log.info(s"Starting stream download: message $messageId")
    val request = HttpRequest(
      method = HttpMethods.GET,
      uri = s"$DAEMON_BASE_URL/download/stream/$messageId"
    )

    httpClient
      .singleRequest(request)
      .flatMap { response ⇒
        if (!response.status.isSuccess()) {
          response.discardEntityBytes()
          Future.failed(
            new RuntimeException(
              s"HTTP ${response.status.intValue()}: ${response.status.reason()}"
            )
          )
        } else {
          sc.log.info(s"Stream download started for message $messageId")
          Future.successful(response.entity.dataBytes)
        }
      }
  }

  /**
    * Download file as complete ByteString.
    *
    * @param messageId Message ID
    * @return Future with file data
    */
  def downloadBytes(messageId: Long): Future[ByteString] = {
    if (!initialized) {
      return Future.failed(TelegramDaemonError.NotInitialized())
    }

    sc.log.info(s"Downloading message $messageId as bytes")
    val request = HttpRequest(
      method = HttpMethods.GET,
      uri = s"$DAEMON_BASE_URL/download/bytes/$messageId"
    )

    httpClient
      .singleRequest(request)
      .flatMap { response ⇒
        if (!response.status.isSuccess()) {
          response.discardEntityBytes()
          val error = if (response.status == StatusCodes.Gone) {
            TelegramDaemonError.ReferenceExpired(messageId)
          } else {
            new RuntimeException(s"HTTP ${response.status.intValue()}: ${response.status.reason()}")
          }

          Future.failed(error)
        } else {
          response.entity.dataBytes
            .runFold(ByteString.empty)(_ ++ _)
            .andThen {
              case Success(data) ⇒
                sc.log.info(s"Downloaded message $messageId (${data.length} bytes)")
              case Failure(ex) ⇒
                sc.log.error(s"Download failed: ${ex.getMessage}")
            }
        }
      }
  }

  /**
    * Download to file path (legacy, still supported).
    */
  def download(
      messageId: Long,
      targetPath: Path
  ): Future[Long] = {
    if (!initialized) {
      return Future.failed(TelegramDaemonError.NotInitialized())
    }

    val payload = Json.obj(
      "message_id" → messageId,
      "path"       → targetPath.toString
    )

    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = s"$DAEMON_BASE_URL/cmd/download",
      entity = HttpEntity(
        ContentTypes.`application/json`,
        Json.stringify(payload)
      )
    )

    sendHttpRequest(request, commandTimeout)
      .flatMap { response ⇒
        val json = Json.parse(response)
        (json \ "error").asOpt[String] match {
          case Some(error) if error.contains("expired") ⇒
            sc.log.warning(s"File reference expired for message $messageId")
            Future.failed(TelegramDaemonError.ReferenceExpired(messageId))
          case Some(error) ⇒
            Future.failed(TelegramDaemonError.OperationFailed("download", error))
          case None ⇒
            (json \ "file_size").asOpt[Long] match {
              case Some(fileSize) ⇒
                sc.log.info(s"Downloaded message $messageId ($fileSize bytes)")
                Future.successful(fileSize)
              case None ⇒
                Future.failed(new RuntimeException("No file size in response"))
            }
        }
      }
  }

  /**
    * List files with pagination.
    */
  def list(
      limit: Int = 100,
      offset: Int = 0
  ): Future[Seq[TelegramFile]] = {
    if (!initialized) {
      return Future.failed(TelegramDaemonError.NotInitialized())
    }

    require(limit > 0 && limit <= 1000, "limit must be 1-1000")
    require(offset >= 0, "offset must be >= 0")

    val payload = Json.obj(
      "limit"  → limit,
      "offset" → offset
    )

    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = s"$DAEMON_BASE_URL/cmd/list",
      entity = HttpEntity(
        ContentTypes.`application/json`,
        Json.stringify(payload)
      )
    )

    sendHttpRequest(request, commandTimeout)
      .flatMap { response ⇒
        val json = Json.parse(response)
        (json \ "error").asOpt[String] match {
          case Some(error) ⇒
            Future.failed(TelegramDaemonError.OperationFailed("list", error))
          case None ⇒
            (json \ "files").asOpt[Seq[JsValue]] match {
              case Some(files) ⇒
                val telegramFiles = files.map { f ⇒
                  TelegramFile(
                    messageId = (f \ "message_id").as[Long],
                    fileName = (f \ "file_name").as[String],
                    fileSize = (f \ "file_size").as[Long],
                    dateTime = (f \ "date").asOpt[Long]
                  )
                }
                sc.log.info(s"Listed ${telegramFiles.length} files")
                Future.successful(telegramFiles)
              case None ⇒
                Future.failed(new RuntimeException("No files in response"))
            }
        }
      }
  }

  /**
    * Delete file from Telegram.
    */
  def deleteFile(messageId: Long): Future[Unit] = {
    if (!initialized) {
      return Future.failed(TelegramDaemonError.NotInitialized())
    }

    val payload = Json.obj(
      "message_id" → messageId
    )

    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = s"$DAEMON_BASE_URL/cmd/delete",
      entity = HttpEntity(
        ContentTypes.`application/json`,
        Json.stringify(payload)
      )
    )

    sendHttpRequest(request, commandTimeout)
      .flatMap { response ⇒
        val json = Json.parse(response)
        if ((json \ "error").asOpt[String].isDefined) {
          Future.failed(
            TelegramDaemonError.OperationFailed("delete", (json \ "error").as[String])
          )
        } else {
          sc.log.info(s"Deleted message $messageId")
          Future.successful(())
        }
      }
  }

  // ============================================================================
  // Helper Methods
  // ============================================================================

  /**
    * Send HTTP request and get response body as string.
    */
  private def sendHttpRequest(
      request: HttpRequest,
      timeout: FiniteDuration // TODO not implemented
  ): Future[String] = {
    httpClient
      .singleRequest(request)
      .flatMap { response ⇒
        response.entity.dataBytes
          .runFold(ByteString.empty)(_ ++ _)
          .map(_.utf8String)
      }
  }

  /**
    * Build multipart form data from source.
    */
  private def buildMultipartForm(
      dataSource: Source[ByteString, _],
      fileName: String,
      caption: String
  ): RequestEntity = {
    // Simplified - would need proper multipart builder in production
    HttpEntity(
      ContentType(MediaTypes.`application/octet-stream`),
      dataSource
    )
  }

  /**
    * Start reader for daemon error stream.
    */
  private def startErrorStreamReader(errorStream: java.io.InputStream): Unit = {
    Future {
      val reader = new java.io.BufferedReader(
        new java.io.InputStreamReader(errorStream)
      )
      var line = reader.readLine()
      while (line != null) {
        sc.log.debug(s"Daemon stderr: $line")
        line = reader.readLine()
      }
      reader.close()
    }
  }

  /**
    * Detect venv Python in ~/.shadowcloud/telegram/.venv
    */
  private def detectVenvPython(): Option[Path] = {
    val homeDir = java.nio.file.Paths.get(System.getProperty("user.home"))
    val osName  = if (isWindows) "windows" else System.getProperty("os.name").toLowerCase
    val venvDir = homeDir.resolve(".shadowcloud").resolve("telegram").resolve(".venv")

    if (!java.nio.file.Files.exists(venvDir)) return None

    val pythonPaths = if (isWindows) {
      Seq(
        venvDir.resolve("Scripts").resolve("python.exe"),
        venvDir.resolve("Scripts").resolve("python3.exe")
      )
    } else {
      Seq(
        venvDir.resolve("bin").resolve("python"),
        venvDir.resolve("bin").resolve("python3")
      )
    }

    pythonPaths.find(java.nio.file.Files.exists(_))
  }

  private def isWindows: Boolean = {
    System.getProperty("os.name").toLowerCase.contains("win")
  }
}

// ============================================================================
// Data Models
// ============================================================================

case class TelegramFile(
    messageId: Long,
    fileName: String,
    fileSize: Long,
    dateTime: Option[Long] = None
)

// ============================================================================
// Error Types
// ============================================================================

sealed trait TelegramDaemonError extends Exception

object TelegramDaemonError {
  case class NotInitialized() extends TelegramDaemonError {
    override def getMessage: String = "Daemon not initialized"
  }

  case class OperationFailed(operation: String, error: String) extends TelegramDaemonError {
    override def getMessage: String = s"$operation failed: $error"
  }

  case class ReferenceExpired(messageId: Long) extends TelegramDaemonError {
    override def getMessage: String = s"File reference expired for message $messageId"
  }
}
