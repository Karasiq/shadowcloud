package com.karasiq.shadowcloud.storage.telegram

import java.io.{IOException, InputStream}
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import akka.util.ByteString
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.storage.telegram.TelegramStorageConfig.Secrets
import com.karasiq.shadowcloud.ui.UIProvider

import scala.util.Try

object TelegramScripts {
  def createSession(storageId: StorageId, secrets: Secrets, uiProvider: UIProvider): ByteString = {
    require(uiProvider.canBlock, "Please create session manually and paste it as base64 in storage config session key")
    val baseDir = Paths.get(sys.props("user.home"), s"tgcloud-temp-$storageId")
    deleteDir(baseDir)
    extract(baseDir)
    writeSecrets(baseDir, secrets)

    uiProvider.showNotification(
      s"""Please execute the following action depending on your OS, then press OK
         |Windows: Execute create_session.bat in $baseDir
         |Linux/MacOS: Run in terminal: $baseDir/create_session
         |""".stripMargin
    )
    val result = readSession(baseDir, secrets.entity)
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
      "telegram_create_session.py"
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
