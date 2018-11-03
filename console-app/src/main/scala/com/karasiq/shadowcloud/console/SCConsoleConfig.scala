package com.karasiq.shadowcloud.console

import java.nio.file.{Files, Paths}

import com.typesafe.config.{Config, ConfigFactory}

import com.karasiq.common.configs.ConfigUtils

object SCConsoleConfig {
  def load(): Config = {
    val autoParallelismConfig = {
      val cpuAvailable = Runtime.getRuntime.availableProcessors()
      val parallelism = math.max(8, math.min(1, cpuAvailable / 2))
      ConfigFactory.parseString(s"shadowcloud.parallelism.default = $parallelism")
    }

    val substitutionsConfig = ConfigFactory.parseResourcesAnySyntax("sc-substitutions")

    val defaultConfig = ConfigFactory.defaultOverrides()
      .withFallback(ConfigFactory.defaultApplication())
      .withFallback(ConfigFactory.defaultReference())

    val serverAppConfig = {
      val fileConfig = {
        val configFilePath = sys.props.getOrElse("shadowcloud.external-config", "shadowcloud.conf")
        val optionalConfFile = Paths.get(configFilePath)
        if (Files.isRegularFile(optionalConfFile))
          ConfigFactory.parseFile(optionalConfFile.toFile)
        else
          ConfigUtils.emptyConfig
      }

      val consoleConfig = ConfigFactory.parseResourcesAnySyntax("sc-console")

      fileConfig
        .withFallback(substitutionsConfig)
        .withFallback(autoParallelismConfig)
        .withFallback(consoleConfig)
        .withFallback(defaultConfig)
        .resolve()
    }

    serverAppConfig
  }
}
