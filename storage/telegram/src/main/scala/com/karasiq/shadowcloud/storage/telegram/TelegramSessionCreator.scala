package com.karasiq.shadowcloud.storage.telegram

import java.io.{BufferedReader, InputStreamReader, PrintWriter}
import java.nio.file.{Files, Path, Paths}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

import akka.util.ByteString

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.ui.Challenge.AnswerFormat

/**
  * Telegram session creator using official Telegram Desktop API (via OpenTele).
  * Scripts are extracted from JAR resources to ~/.shadowcloud/telegram/
  */
object TelegramSessionCreator {
  private val PROMPT_PHONE = "PROMPT:PHONE"
  private val PROMPT_CODE = "PROMPT:CODE"
  private val PROMPT_PASSWORD = "PROMPT:PASSWORD"
  private val INFO_PREFIX = "INFO:"
  private val ERROR_PREFIX = "ERROR:"
  private val SUCCESS_PREFIX = "SUCCESS:"
  private val SESSION_PREFIX = "SESSION:"
  private val REQUIRES_OPENTELE = "REQUIRES:OPENTELE"

  /**
    * Get existing session or create new one with automatic setup.
    */
  def getOrCreateSession(
                          storageId: String
                        )(implicit sc: ShadowCloudExtension, ec: ExecutionContext): Future[String] = {
    val sessionKey = "telegram-session"

    // Ensure resources are extracted from JAR
    Try(TelegramResourceExtractor.ensureExtracted()) match {
      case Success(_) ⇒
        sc.log.info("Telegram resources extracted from JAR")
      case Failure(ex) ⇒
        sc.log.error(s"Failed to extract Telegram resources: ${ex.getMessage}")
        return Future.failed(ex)
    }

    // Try to load existing session from sc.sessions
    val existingSession = Try {
      sc.sessions
        .getRawBlocking(storageId, sessionKey)
    }.toOption.filter(_.nonEmpty).map(bs ⇒ new String(bs.toArray, "UTF-8"))

    existingSession match {
      case Some(session) ⇒
        sc.log.info(s"Found existing session for $storageId, verifying...")
        verifyOrRecreateSession(storageId, session)
      case None ⇒
        sc.log.info(s"No existing session for $storageId, creating new with official Telegram Desktop API...")
        createSessionAuto(None).map { newSession ⇒
          storeSession(storageId, sessionKey, newSession)
          newSession
        }
    }
  }

  /**
    * Verify existing session, recreate if invalid.
    */
  private def verifyOrRecreateSession(
                                       storageId: String,
                                       existingSession: String
                                     )(implicit sc: ShadowCloudExtension, ec: ExecutionContext): Future[String] = {
    val sessionKey = "telegram-session"

    createSessionAuto(Some(existingSession))
      .map { session ⇒
        if (session != existingSession) {
          sc.log.info(s"Session refreshed for $storageId")
          storeSession(storageId, sessionKey, session)
        } else {
          sc.log.info(s"Session verified for $storageId")
        }
        session
      }
      .recoverWith { case ex ⇒
        sc.log.warning(s"Session verification failed for $storageId, creating new: ${ex.getMessage}")
        Try(sc.sessions.setRawBlocking(storageId, sessionKey, null))
        createSessionAuto(None).map { newSession ⇒
          storeSession(storageId, sessionKey, newSession)
          newSession
        }
      }
  }

  /**
    * Store session string in sc.sessions.
    */
  private def storeSession(
                            storageId: String,
                            sessionKey: String,
                            sessionString: String
                          )(implicit sc: ShadowCloudExtension): Unit = {
    Try {
      sc.sessions.setRawBlocking(
        storageId,
        sessionKey,
        ByteString(sessionString, "UTF-8")
      )
      sc.log.info(s"Session stored for $storageId (length: ${sessionString.length})")
    }.recover { case ex ⇒
      sc.log.error(s"Failed to store session for $storageId: ${ex.getMessage}")
      throw ex
    }
  }

  /**
    * Create session with automatic configuration.
    * Uses extracted resources from JAR.
    */
  def createSessionAuto(
                         existingSession: Option[String] = None
                       )(implicit sc: ShadowCloudExtension, ec: ExecutionContext): Future[String] = {
    val venvPython = detectVenvPython()
    val scriptPath = TelegramResourceExtractor.getPythonScriptPath

    venvPython match {
      case Some(python) ⇒
        sc.log.info(s"Using venv Python: $python")
        createSession(python.toString, scriptPath, existingSession)
      case None ⇒
        sc.log.warning("Venv not found in ~/.shadowcloud/telegram, attempting to create it...")
        setupVenvAndCreateSession(existingSession)
    }
  }

  /**
    * Setup venv using extracted setup script.
    */
  private def setupVenvAndCreateSession(
                                         existingSession: Option[String]
                                       )(implicit sc: ShadowCloudExtension, ec: ExecutionContext): Future[String] = {
    Future {
      sc.log.info("Setting up Python environment with uv in ~/.shadowcloud/telegram...")
      val setupScript = TelegramResourceExtractor.getSetupScriptPath

      if (!Files.exists(setupScript)) {
        throw new IllegalStateException(
          s"Setup script not found (failed to extract from JAR): $setupScript"
        )
      }

      val setupCommand = if (isWindows) {
        Seq("powershell.exe", "-ExecutionPolicy", "Bypass", "-File", setupScript.toString)
      } else {
        Seq("bash", setupScript.toString)
      }

      val processBuilder = new ProcessBuilder(setupCommand: _*)
      processBuilder.directory(TelegramResourceExtractor.getTargetDirectory.toFile)
      processBuilder.redirectErrorStream(true)

      val process = processBuilder.start()
      val reader = new BufferedReader(new InputStreamReader(process.getInputStream))

      var line = reader.readLine()
      while (line != null) {
        sc.log.info(s"Setup: $line")
        line = reader.readLine()
      }

      val exitCode = process.waitFor()
      if (exitCode != 0) {
        throw new RuntimeException(s"Setup failed with exit code: $exitCode")
      }

      val venvPython = detectVenvPython().getOrElse {
        throw new IllegalStateException("Failed to create venv in ~/.shadowcloud/telegram")
      }
      val scriptPath = TelegramResourceExtractor.getPythonScriptPath

      scala.concurrent.Await.result(
        createSession(venvPython.toString, scriptPath, existingSession),
        scala.concurrent.duration.Duration.Inf
      )
    }
  }

  /**
    * Create or verify Telegram session using official Desktop API.
    */
  def createSession(
                     pythonPath: String,
                     scriptPath: Path,
                     existingSession: Option[String] = None
                   )(implicit sc: ShadowCloudExtension, ec: ExecutionContext): Future[String] = {
    val promise = Promise[String]()

    Future {
      if (!Files.exists(scriptPath)) {
        throw new IllegalArgumentException(s"Python script not found: $scriptPath")
      }

      val args = Seq(
        pythonPath,
        scriptPath.toString
      ) ++ existingSession.map(s ⇒ s"session:$s").toSeq

      val mode = if (existingSession.isDefined) "verifying" else "creating new"
      sc.log.info(s"Telegram session $mode with official Desktop API")

      val processBuilder = new ProcessBuilder(args: _*)
      processBuilder.redirectErrorStream(true)

      val process = processBuilder.start()
      val writer = new PrintWriter(process.getOutputStream, true)
      val reader = new BufferedReader(new InputStreamReader(process.getInputStream))

      try {
        var sessionString: Option[String] = None
        var running = true

        while (running) {
          val line = reader.readLine()
          if (line == null) {
            val exitCode = process.waitFor()
            if (exitCode != 0 && sessionString.isEmpty) {
              throw new RuntimeException(s"Process failed with exit code: $exitCode")
            }
            running = false
          } else {
            line match {
              case PROMPT_PHONE ⇒
                sc.log.info("Requesting phone number from user via challenge")
                val phoneFuture = sc.challenges
                  .create(
                    title = "Telegram Phone Number",
                    html = "Enter your Telegram phone number with country code\n\nExample: `+1234567890`",
                    answerFormat = AnswerFormat.String,
                    deadline = 10.minutes
                  )
                  .map(bs ⇒ bs.utf8String.trim)

                val phone = scala.concurrent.Await.result(phoneFuture, 11.minutes)
                sc.log.info("Phone number received")
                writer.println(phone)
                writer.flush()

              case PROMPT_CODE ⇒
                sc.log.info("Requesting verification code from user via challenge")
                val codeFuture = sc.challenges
                  .create(
                    title = "Telegram Verification Code",
                    html = "Enter the 5-digit verification code sent to your Telegram app",
                    answerFormat = AnswerFormat.String,
                    deadline = 5.minutes
                  )
                  .map(bs ⇒ bs.utf8String.trim)

                val code = scala.concurrent.Await.result(codeFuture, 6.minutes)
                sc.log.info("Verification code received")
                writer.println(code)
                writer.flush()

              case PROMPT_PASSWORD ⇒
                sc.log.info("Requesting 2FA password from user via challenge")
                val passwordFuture = sc.challenges
                  .create(
                    title = "Telegram Cloud Password (2FA)",
                    html = "Your account has Two-Factor Authentication enabled.\n\nEnter your Telegram Cloud Password:",
                    answerFormat = AnswerFormat.String,
                    deadline = 10.minutes
                  )
                  .map(bs ⇒ bs.utf8String.trim)

                val password = scala.concurrent.Await.result(passwordFuture, 11.minutes)
                sc.log.info("2FA password received")
                writer.println(password)
                writer.flush()

              case REQUIRES_OPENTELE ⇒
                sc.log.error("OpenTele not installed - required for official Desktop API")
                sc.ui.showNotification(
                  "OpenTele Required\n" +
                    "Installing Python dependencies...\nThis may take a few minutes"
                )
                throw new RuntimeException("OpenTele required but not installed")

              case info if info.startsWith(INFO_PREFIX) ⇒
                sc.log.info(s"Telegram: ${info.stripPrefix(INFO_PREFIX).trim}")

              case error if error.startsWith(ERROR_PREFIX) ⇒
                val msg = error.stripPrefix(ERROR_PREFIX).trim
                sc.log.error(s"Telegram: $msg")
                throw new RuntimeException(s"Authentication failed: $msg")

              case success if success.startsWith(SUCCESS_PREFIX) ⇒
                sc.log.info(s"Telegram: ${success.stripPrefix(SUCCESS_PREFIX).trim}")

              case session if session.startsWith(SESSION_PREFIX) ⇒
                sessionString = Some(session.stripPrefix(SESSION_PREFIX).trim)
                running = false

              case other ⇒
                sc.log.debug(s"Telegram: $other")
            }
          }
        }

        sessionString match {
          case Some(s) if s.nonEmpty ⇒ promise.success(s)
          case _ ⇒ promise.failure(new RuntimeException("No session received"))
        }
      } catch {
        case ex: Exception ⇒ promise.failure(ex)
      } finally {
        Try {
          writer.close()
          reader.close()
          if (process.isAlive) process.destroyForcibly()
        }
      }
    }.recover { case ex ⇒
      promise.tryFailure(ex)
    }

    promise.future
  }

  /**
    * Detect venv Python in ~/.shadowcloud/telegram/.venv
    */
  private def detectVenvPython(): Option[Path] = {
    val homeDir = Paths.get(System.getProperty("user.home"))
    val venvDir = homeDir.resolve(".shadowcloud").resolve("telegram").resolve(".venv")

    if (!Files.exists(venvDir)) return None

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

    pythonPaths.find(Files.exists(_))
  }

  private def isWindows: Boolean = {
    System.getProperty("os.name").toLowerCase.contains("win")
  }
}