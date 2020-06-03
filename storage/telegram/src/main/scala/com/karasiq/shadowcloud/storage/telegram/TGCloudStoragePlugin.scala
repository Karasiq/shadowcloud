package com.karasiq.shadowcloud.storage.telegram

import java.nio.file.{Files, Paths}

import akka.actor.{ActorContext, ActorRef}
import akka.event.Logging
import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.common.encoding.Base64
import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.model.utils.StorageHealth
import com.karasiq.shadowcloud.providers.LifecycleHook
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.utils.StoragePluginBuilder
import com.karasiq.shadowcloud.storage.{StorageHealthProvider, StoragePlugin}

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{Random, Try}

class TGCloudStoragePlugin extends StoragePlugin {
  override def createStorage(storageId: StorageId, props: StorageProps)(implicit context: ActorContext): ActorRef = {
    import context.system

    val sc     = ShadowCloud()
    val log    = Logging(system, context.self)
    val config = TGCloudConfig(props.rootConfig.withFallback(sc.config.rootConfig.getConfig("storage.tgcloud")))
    val port   = config.port.getOrElse(50000 + Random.nextInt(65535 - 50000))

    var runningProc: Process = null

    val repository = TGCloudRepository(port)
    StoragePluginBuilder(storageId, props)
      .withIndexTree(repository)
      .withChunksTree(repository)
      .withHealth(new StorageHealthProvider {
        override def health: Future[StorageHealth] = {
          /* for {
          used <- repository.fileSizes(StoragePluginBuilder.getRootPath(props))
        } yield StorageHealth.unlimited.copy(usedSpace = used) */
          Future.successful(StorageHealth.unlimited.copy(online = Option(runningProc).forall(_.isAlive)))
        }
      })
      .withLifecycleHook(new LifecycleHook {
        private[this] val scriptsPath = Files.createTempDirectory("tgcloud")
        private[this] var threads     = Seq.empty[Thread]

        override def initialize(): Unit = if (config.port.isEmpty) {
          val session = Try(sc.sessions.getRawBlocking(storageId, "tgcloud-session")).toOption
            .orElse(props.rootConfig.optional(_.getString("session")).map(Base64.decode))
            .filter(_.nonEmpty)
            .getOrElse(TGCloudScripts.createSession(config.secrets, sc.ui))

          require(session.nonEmpty, "Session is empty")
          sc.sessions.setRawBlocking(storageId, "tgcloud-session", session)

          val process = {
            TGCloudScripts.extract(scriptsPath)
            TGCloudScripts.writeSecrets(scriptsPath, config.secrets)
            TGCloudScripts.writeSession(scriptsPath, config.secrets.entity, session)

            val python = config.pythonPath.getOrElse {
              val paths = Seq("/usr/local/bin/python3", "/usr/bin/python3", "/usr/local/bin/python", "/usr/bin/python", "C:\\Windows\\py.exe")
              paths
                .find(f ⇒ Files.isExecutable(Paths.get(f)))
                .getOrElse(throw new IllegalStateException("Python executable is not found. Please provide an explicit 'python-path' setting."))
            }
            new ProcessBuilder(python, "download_service.py", "server", port.toString, config.entity).directory(scriptsPath.toFile)
          }
          log.info("Starting subprocess: {}", process.command().asScala.mkString(" "))
          runningProc = process.start()

          val stdoutReader = new Thread(() ⇒ {
            scala.io.Source
              .fromInputStream(runningProc.getInputStream, "UTF-8")
              .getLines()
              .foreach(log.info("(Python) {}", _))
          })
          val stderrReader = new Thread(() ⇒ {
            scala.io.Source
              .fromInputStream(runningProc.getErrorStream, "UTF-8")
              .getLines()
              .foreach(log.info("Python: {}", _))
          })
          stdoutReader.setName(s"python-$port-stdout")
          stderrReader.setName(s"python-$port-stderr")
          threads = Seq(stdoutReader, stderrReader)
          threads.foreach(_.setDaemon(true))
          threads.foreach(_.start())
          sys.addShutdownHook(shutdown())
        }

        override def shutdown(): Unit = synchronized {
          Option(runningProc).foreach(_.destroy())
          TGCloudScripts.deleteDir(scriptsPath)
          threads.foreach(_.interrupt())
          runningProc = null
          threads = Nil
        }
      })
      .createStorage()
  }
}
