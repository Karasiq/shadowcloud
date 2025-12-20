package com.karasiq.shadowcloud.storage.telegram

import java.nio.file.Path

import scala.concurrent.duration.{DurationInt, FiniteDuration}

case class TelegramStorageConfig(pythonPath: String, entity: String, tempDir: Path, sessionCreationTimeout: FiniteDuration,
                                 daemonInitTimeout: FiniteDuration)

object TelegramStorageConfig {

  def apply(config: com.typesafe.config.Config): TelegramStorageConfig = {
    import com.karasiq.common.configs.ConfigImplicits._

    val pythonPath = config.withDefault("python", _.getString("python-path"))
    val entity = config.withDefault("me", _.getString("entity"))
    val sessionTimeoutSeconds = config.withDefault(600, _.getInt("session-creation-timeout-seconds"))
    val daemonInitTimeoutSeconds = config.withDefault(60, _.getInt("daemon-init-timeout-seconds"))

    val sessionCreationTimeout = sessionTimeoutSeconds.seconds
    val daemonInitTimeout = daemonInitTimeoutSeconds.seconds

    val tempDirPath = config.withDefault(
      System.getProperty("user.home") + "/.shadowcloud/telegram",
      _.getString("temp-dir")
    )

    val tempDir = java.nio.file.Paths.get(tempDirPath)
    if (!java.nio.file.Files.exists(tempDir)) {
      java.nio.file.Files.createDirectories(tempDir)
    }

    TelegramStorageConfig(
      pythonPath,
      entity = entity,
      tempDir = tempDir,
      sessionCreationTimeout = sessionCreationTimeout,
      daemonInitTimeout = daemonInitTimeout,
    )
}
