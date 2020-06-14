package com.karasiq.shadowcloud.storage.telegram

import java.nio.file.{Files, Paths}

import akka.actor.{ActorContext, ActorRef}
import akka.event.Logging
import akka.stream.scaladsl.{RestartSource, Sink, Source}
import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.common.encoding.Base64
import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.model.utils.StorageHealth
import com.karasiq.shadowcloud.providers.LifecycleHook
import com.karasiq.shadowcloud.storage.StoragePlugin
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.utils.StoragePluginBuilder

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.sys.ShutdownHookThread
import scala.util.control.NonFatal
import scala.util.{Random, Try}

class TelegramStoragePlugin extends StoragePlugin {
  override def createStorage(storageId: StorageId, props: StorageProps)(implicit context: ActorContext): ActorRef = {
    import context.{dispatcher, system}

    val sc     = ShadowCloud()
    val log    = Logging(system, context.self)
    val config = TelegramStorageConfig(props.rootConfig.withFallback(sc.config.rootConfig.getConfig("storage.telegram")))
    val port   = config.port.getOrElse(50000 + Random.nextInt(65535 - 50000))

    var runningProc: Process = null

    val repository = TelegramRepository(port)
    StoragePluginBuilder(storageId, props)
      .withTreeRepo(repository)
      .withHealthF(() ⇒ repository.fileSizes(StoragePluginBuilder.getRootPath(props)).map(used ⇒ StorageHealth.unlimited.copy(usedSpace = used)))
      .withLifecycleHook(new LifecycleHook {
        private[this] val scriptsPath                      = Files.createTempDirectory("tgcloud")
        private[this] var threads                          = Seq.empty[Thread]
        private[this] var shutdownHook: ShutdownHookThread = null

        override def initialize(): Unit = {
          // Start process
          if (config.port.isEmpty) {
            val session = Try(sc.sessions.getRawBlocking(storageId, "tgcloud-session")).toOption
              .orElse(props.rootConfig.optional(_.getString("session")).map(Base64.decode))
              .filter(_.nonEmpty)
              .getOrElse(TelegramScripts.createSession(storageId, config.secrets, sc.ui))

            require(session.nonEmpty, "Session is empty")
            sc.sessions.setRawBlocking(storageId, "tgcloud-session", session)

            val process = {
              TelegramScripts.extract(scriptsPath)
              TelegramScripts.writeSecrets(scriptsPath, config.secrets)
              TelegramScripts.writeSession(scriptsPath, config.secrets.entity, session)

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
                .foreach(log.debug("(Python) {}", _))
            })
            val stderrReader = new Thread(() ⇒ {
              scala.io.Source
                .fromInputStream(runningProc.getErrorStream, "UTF-8")
                .getLines()
                .foreach(log.error("Python: {}", _))
            })
            stdoutReader.setName(s"python-$port-stdout")
            stderrReader.setName(s"python-$port-stderr")
            threads = Seq(stdoutReader, stderrReader)
            threads.foreach(_.setDaemon(true))
            threads.foreach(_.start())
            shutdownHook = sys.addShutdownHook(shutdown())
          }

          val startSource = RestartSource.onFailuresWithBackoff(500 millis, 3 seconds, 0.2, 15) { () ⇒
            Source.future(repository.fileSizes("__alive-test__"))
          }
          val future = startSource
            .completionTimeout(15 seconds)
            .runWith(Sink.head)

          try {
            Await.result(future, Duration.Inf)
          } catch {
            case NonFatal(err) ⇒
              throw new IllegalStateException(s"TGCloud process not responding: $runningProc at port $port", err)
          }
        }

        override def shutdown(): Unit = synchronized {
          Option(runningProc).foreach(_.destroy())
          TelegramScripts.deleteDir(scriptsPath)
          threads.foreach(_.interrupt())
          runningProc = null
          threads = Nil
          Try(Option(shutdownHook).foreach(_.remove()))
          shutdownHook = null
        }

        override def toString: StorageId = s"TGCloudProcessLifecycleHook($runningProc)"
      })
      .createStorage()
  }
}
