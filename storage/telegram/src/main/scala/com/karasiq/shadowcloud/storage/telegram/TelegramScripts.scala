package com.karasiq.shadowcloud.storage.telegram

import java.io.{IOException, InputStream}
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import akka.util.ByteString
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.storage.telegram.TelegramStorageConfig.Secrets
import com.karasiq.shadowcloud.ui.Challenge.AnswerFormat
import com.karasiq.shadowcloud.ui.ChallengeHub

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

object TelegramScripts {
  def createSession(storageId: StorageId, secrets: Secrets, tempDir: String, uiProvider: ChallengeHub): ByteString = {
    val baseDir = Paths.get(tempDir, storageId)
    deleteDir(baseDir)
    extract(baseDir)
    writeSecrets(baseDir, secrets)

    val future = uiProvider.create(
      s"Telegram login ($storageId)",
      s"""Please execute the following action depending on your OS<br>
         |<b>Windows</b>: Execute <code>create_session.bat</code> in $baseDir<br>
         |<b>Linux/MacOS</b>: Run in terminal: <code>bash $baseDir/create_session</code><br>
         |Then upload created <code>${secrets.entity}.session</code> file
         |Also you can use an <a href="https://github.com/Karasiq/shadowcloud/releases/download/v1.2.6/tgcloud_scripts.7z">external scripts package</a> on remote environments
         |""".stripMargin,
      AnswerFormat.Binary
    )

    val result = Await.result(future, Duration.Inf)
    deleteDir(baseDir)
    result
  }

  def writeSecrets(directory: Path, secrets: Secrets): Unit = {
    val secretsStr =
      s"""api_hash = "${secrets.apiHash}"
        |api_id = ${secrets.apiId}
        |entity = "${secrets.entity}"
        |""".stripMargin

    Files.write(directory.resolve("secret.py"), secretsStr.getBytes("UTF-8"))
  }

  def readSession(baseDir: Path, entity: String): ByteString = {
    val sessionFile = baseDir.resolve(s"${entity}.session")
    val result      = ByteString.fromArrayUnsafe(Files.readAllBytes(sessionFile))
    result
  }

  def writeSession(directory: Path, entity: String, session: ByteString): Unit = {
    Files.write(directory.resolve(s"$entity.session"), session.toArray)
  }

  def extract(directory: Path): Unit = {
    val files = Seq(
      "download_service.py",
      "requirements.txt",
      "telegram_create_session.py",
      "create_session",
      "create_session.bat"
    )
    Files.createDirectories(directory)
    files.foreach { f ⇒
      val stream = getResource(s"tgcloud/$f")
      try Files.copy(stream, directory.resolve(f))
      finally stream.close()
    }
  }

  def deleteDir(baseDir: Path): Unit = {
    def doDelete(): Try[Unit] = Try {
      if (Files.isDirectory(baseDir))
        Files.walkFileTree(
          baseDir,
          new SimpleFileVisitor[Path] {
            override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
              Files.deleteIfExists(file)
              FileVisitResult.CONTINUE
            }

            override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
              Files.deleteIfExists(dir)
              FileVisitResult.CONTINUE
            }
          }
        )
    }

    doDelete().failed.foreach(_ ⇒ sys.addShutdownHook(doDelete()))
  }

  def getResource(name: String): InputStream = {
    getClass.getClassLoader.getResourceAsStream(name)
  }
}
