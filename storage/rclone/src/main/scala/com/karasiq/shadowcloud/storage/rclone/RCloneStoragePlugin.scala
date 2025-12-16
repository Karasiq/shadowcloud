package com.karasiq.shadowcloud.storage.rclone

import akka.actor.{ActorContext, ActorRef}
import akka.event.Logging
import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.model.utils.StorageHealth
import com.karasiq.shadowcloud.providers.LifecycleHook
import com.karasiq.shadowcloud.storage.StoragePlugin
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.utils.StoragePluginBuilder

import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.{Try, Using, Failure, Success}
import java.nio.file.{Files, Paths}
import scala.collection.JavaConverters._

class RCloneStoragePlugin extends StoragePlugin {
  override def createStorage(storageId: StorageId, props: StorageProps)(implicit context: ActorContext): ActorRef = {
    import context.{dispatcher, system}

    val sc = ShadowCloud()
    val log = Logging(system, context.self)
    val config = RCloneStorageConfig(
      props.rootConfig.withFallback(sc.config.rootConfig.getConfig("storage.rclone"))
    )

    Try {
      // Validate RClone binary exists and is executable
      val rclonePath = config.rclonePath
      validateRCloneBinary(rclonePath, log)

      // Validate remote exists in RClone config
      val remoteName = config.remoteName
      validateRCloneRemote(rclonePath, remoteName, log)

      // Create repository
      val repository = RCloneRepository(remoteName, rclonePath)(dispatcher)

      // Build storage with health monitoring
      StoragePluginBuilder(storageId, props)
        .withTreeRepo(repository)
        .withHealthF(() ⇒ getRCloneHealth(rclonePath, remoteName, config)(dispatcher))
        .withLifecycleHook(new LifecycleHook {
          override def initialize(): Unit = {
            log.info(
              "Initializing RClone storage: remote={}, type={}",
              remoteName,
              config.remoteType
            )

            // Test RClone connectivity
            Try {
              val exitCode = scala.sys.process.Process(
                Seq(rclonePath, "lsf", s"$remoteName:")
              ).!(scala.sys.process.ProcessLogger(_ ⇒ ()))
              
              if (exitCode != 0) {
                throw new IllegalStateException(s"RClone remote test failed with code: $exitCode")
              }
            } match {
              case Success(_) ⇒
                log.info("RClone remote connectivity test passed")
              case Failure(ex) ⇒
                log.error(ex, "RClone remote test failed")
                throw new IllegalStateException(s"RClone initialization failed: ${ex.getMessage}", ex)
            }
          }

          override def shutdown(): Unit = {
            log.info("Shutting down RClone storage: remote={}", remoteName)
          }

          override def toString: StorageId = s"RCloneLifecycleHook($remoteName)"
        })
        .createStorage()

    } match {
      case Success(storage) ⇒ storage
      case Failure(ex) ⇒
        log.error(ex, "Failed to create RClone storage")
        sc.ui.showNotification(
          s"Failed to initialize RClone storage: $storageId\n${ex.getMessage}"
        )
        context.stop(context.self)
        throw ex
    }
  }

  /**
   * Validate RClone binary exists and is executable.
   */
  private def validateRCloneBinary(rclonePath: String, log: akka.event.LoggingAdapter): Unit = {
    Try(blocking {
      val pb = new ProcessBuilder(rclonePath, "version").redirectErrorStream(true)
      val process = pb.start()
      val output = scala.io.Source.fromInputStream(process.getInputStream)
        .getLines()
        .mkString("\n")
      val exitCode = process.waitFor()
      
      if (exitCode != 0) {
        throw new IllegalStateException(s"RClone version check failed: $output")
      }

      log.info("RClone version info: {}", output.split("\n").headOption.getOrElse("Unknown"))
    }) match {
      case Failure(ex) ⇒
        throw new IllegalArgumentException(
          s"RClone binary not found or not executable at: $rclonePath\n${ex.getMessage}",
          ex
        )
      case Success(_) ⇒
        log.info("RClone binary validated: {}", rclonePath)
    }
  }

  /**
   * Validate RClone remote is configured.
   */
  private def validateRCloneRemote(
    rclonePath: String,
    remoteName: String,
    log: akka.event.LoggingAdapter
  ): Unit = {
    Try(blocking {
      val pb = new ProcessBuilder(rclonePath, "listremotes").redirectErrorStream(true)
      val process = pb.start()
      val remotes = scala.io.Source.fromInputStream(process.getInputStream)
        .getLines()
        .map(_.trim.stripSuffix(":"))
        .toSet
      val exitCode = process.waitFor()
      
      if (exitCode != 0 || !remotes.contains(remoteName)) {
        throw new IllegalStateException(
          s"Remote '$remoteName' not found. Available: ${remotes.mkString(", ")}"
        )
      }

      log.info("RClone remote validated: {}", remoteName)
    }) match {
      case Failure(ex) ⇒
        throw new IllegalArgumentException(
          s"RClone remote validation failed: ${ex.getMessage}",
          ex
        )
      case Success(_) ⇒ ()
    }
  }

  /**
   * Get RClone storage health and quota information.
   */
  private def getRCloneHealth(
    rclonePath: String,
    remoteName: String,
    config: RCloneStorageConfig
  )(implicit ec: ExecutionContext): Future[StorageHealth] = {
    Future(blocking {
      Try {
        val pb = new ProcessBuilder(
          rclonePath,
          "about",
          "--full",
          "--json",
          s"$remoteName:"
        ).redirectErrorStream(true)
        val process = pb.start()
        val output = scala.io.Source.fromInputStream(process.getInputStream)
          .getLines()
          .mkString("\n")
        val exitCode = process.waitFor()
        
        if (exitCode == 0 && output.nonEmpty) {
          // Parse JSON to extract quota information
          parseRCloneAbout(output)
        } else {
          // Fallback for remotes that don't support 'about' command
          StorageHealth.unlimited
        }
      }.getOrElse(StorageHealth.unlimited)
    })
  }

  /**
   * Parse RClone 'about' JSON output.
   */
  private def parseRCloneAbout(jsonOutput: String): StorageHealth = {
    try {
      import scala.util.parsing.json.JSON
      
      JSON.parseFull(jsonOutput) match {
        case Some(map: Map[String, Any] @unchecked) ⇒
          val total = map.get("Total")
            .flatMap(_.asInstanceOf[Option[Long]])
            .getOrElse(0L)
          val used = map.get("Used")
            .flatMap(_.asInstanceOf[Option[Long]])
            .getOrElse(0L)

          if (total > 0) {
            StorageHealth.withQuota(
              available = total - used,
              used = used,
              quota = total
            )
          } else {
            StorageHealth.unlimited
          }
        case _ ⇒ StorageHealth.unlimited
      }
    } catch {
      case _: Exception ⇒ StorageHealth.unlimited
    }
  }
}

/**
 * RClone storage configuration.
 */
private[rclone] case class RCloneStorageConfig(
  remoteName: String,
  remoteType: String,
  rclonePath: String,
  configPath: Option[java.nio.file.Path],
  cacheDir: Option[java.nio.file.Path]
)

private[rclone] object RCloneStorageConfig {
  /**
   * Create configuration from Typesafe Config.
   */
  def apply(config: com.typesafe.config.Config): RCloneStorageConfig = {
    val remote = config.getString("remote-name")
    val rtype = config.optional(_.getString("remote-type")).getOrElse("unknown")
    val rpath = config.optional(_.getString("rclone-path")).getOrElse(findRCloneBinary())
    val cpath = config.optional(_.getString("config-path"))
      .map(p ⇒ Paths.get(p))
    val cdir = config.optional(_.getString("cache-dir"))
      .map(p ⇒ Paths.get(p))
      .getOrElse {
        Files.createTempDirectory("rclone-sc")
      }

    RCloneStorageConfig(
      remoteName = remote,
      remoteType = rtype,
      rclonePath = rpath,
      configPath = cpath,
      cacheDir = Some(cdir)
    )
  }

  /**
   * Find RClone binary in system PATH.
   */
  private def findRCloneBinary(): String = {
    val candidates = scala.sys.env
      .get("PATH")
      .map(_.split(java.io.File.pathSeparator).toList)
      .getOrElse(List("/usr/bin", "/usr/local/bin", "/opt/homebrew/bin", "C:\\ProgramData\\chocolatey\\bin\\rclone.exe"))

    candidates
      .flatMap(dir ⇒ Paths.get(dir, "rclone.exe") :: Paths.get(dir, "rclone"))
      .find(Files.isExecutable(_))
      .map(_.toString)
      .getOrElse("rclone")  // Default to PATH lookup
  }
}
