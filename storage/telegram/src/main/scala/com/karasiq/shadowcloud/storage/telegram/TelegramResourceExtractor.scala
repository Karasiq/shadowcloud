package com.karasiq.shadowcloud.storage.telegram

import java.io.{BufferedInputStream, BufferedOutputStream, FileOutputStream}
import java.nio.file.{Files, Path, Paths}

import scala.util.Try

/**
  * Extracts Telegram storage resources (scripts) from JAR to filesystem.
  * Resources are extracted to ~/.shadowcloud/telegram/
  */
object TelegramResourceExtractor {
  private val RESOURCE_BASE = "/tgcloud"

  /**
    * Resource definitions with their target paths.
    */
  private val RESOURCES = Seq(
    // Python session creator script
    ("telegram_create_session.py", "telegram_create_session.py"),
    ("telegram_daemon.py", "telegram_daemon.py"),
    // Setup scripts
    ("setup-venv.sh", "setup-venv.sh"),
    ("setup-venv.ps1", "setup-venv.ps1"),
    // Requirements
    ("requirements.txt", "requirements.txt")
  )

  /**
    * Get the target directory for extracted resources.
    * Returns ~/.shadowcloud/telegram/
    */
  def getTargetDirectory: Path = {
    val homeDir = Paths.get(System.getProperty("user.home"))
    homeDir.resolve(".shadowcloud").resolve("telegram")
  }

  /**
    * Extract all required resources from JAR to filesystem.
    *
    * @return Path to the directory containing extracted resources
    */
  def extractResources(): Try[Path] = Try {
    val targetDir = getTargetDirectory

    // Create target directory if it doesn't exist
    Files.createDirectories(targetDir)

    // Extract each resource
    RESOURCES.foreach { case (resourceName, targetName) ⇒
      extractResource(resourceName, targetDir.resolve(targetName))
    }

    // Make shell scripts executable on Unix systems
    if (!isWindows) {
      makeExecutable(targetDir.resolve("setup-venv.sh"))
    }

    targetDir
  }

  /**
    * Extract a single resource from JAR to filesystem.
    * Only extracts if file doesn't exist or JAR version is newer.
    */
  private def extractResource(resourceName: String, targetPath: Path): Unit = {
    val resourcePath = s"$RESOURCE_BASE/$resourceName"
    val resourceStream = getClass.getResourceAsStream(resourcePath)

    if (resourceStream == null) {
      throw new IllegalStateException(s"Resource not found in JAR: $resourcePath")
    }

    // Check if we need to extract (file doesn't exist or is outdated)
    val shouldExtract = !Files.exists(targetPath) || isResourceNewer(resourceStream, targetPath)

    if (shouldExtract) {
      val inputStream = new BufferedInputStream(getClass.getResourceAsStream(resourcePath))
      val outputStream = new BufferedOutputStream(new FileOutputStream(targetPath.toFile))

      try {
        com.google.common.io.ByteStreams.copy(inputStream, outputStream)
      } finally {
        inputStream.close()
        outputStream.close()
      }
    }
  }

  /**
    * Check if JAR resource is newer than filesystem file.
    * Currently always returns true to ensure latest version is used.
    * Could be enhanced to compare timestamps or checksums.
    */
  private def isResourceNewer(resourceStream: java.io.InputStream, targetPath: Path): Boolean = {
    // For simplicity, always extract to ensure we have the latest version
    // In production, could compare checksums or embedded version numbers
    true
  }

  /**
    * Make a file executable (Unix only).
    */
  private def makeExecutable(path: Path): Unit = {
    if (Files.exists(path)) {
      Try {
        path.toFile.setExecutable(true, false)
      }
    }
  }

  /**
    * Get the path to the extracted Python script.
    */
  def getPythonScriptPath: Path = {
    getTargetDirectory.resolve("telegram_create_session.py")
  }

  /**
    * Get the path to the daemon script.
    */
  def getDaemonScriptPath: Path = {
    getTargetDirectory.resolve("telegram_daemon.py")
  }

  /**
    * Get the path to the setup script for current OS.
    */
  def getSetupScriptPath: Path = {
    val scriptName = if (isWindows) "setup-venv.ps1" else "setup-venv.sh"
    getTargetDirectory.resolve(scriptName)
  }

  /**
    * Ensure all resources are extracted and return target directory.
    */
  def ensureExtracted(): Path = {
    extractResources().getOrElse {
      throw new IllegalStateException("Failed to extract Telegram storage resources from JAR")
    }
  }

  private def isWindows: Boolean = {
    System.getProperty("os.name").toLowerCase.contains("win")
  }
}