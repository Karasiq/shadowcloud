package com.karasiq.shadowcloud.desktop

import java.nio.file.{Files, Paths}

import com.typesafe.config.{Config, ConfigFactory}

import com.karasiq.common.configs.ConfigUtils

object SCDesktopConfig {
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
    /* .withFallback {
      // reference.conf without ".resolve()" workaround

      val classLoader = Thread.currentThread().getContextClassLoader
      ConfigImpl.computeCachedConfig(classLoader, "scReference", () ⇒ {
        val parseOptions = ConfigParseOptions.defaults.setClassLoader(classLoader)
        val unresolvedResources = Parseable.newResources("reference.conf", parseOptions)
          .parse
          .toConfig

        ConfigImpl.systemPropertiesAsConfig()
          .withFallback(unresolvedResources)
      })
    } */

    val serverAppConfig = {
      val fileConfig = {
        val optionalConfFile = Paths.get("shadowcloud.conf")
        if (Files.isRegularFile(optionalConfFile))
          ConfigFactory.parseFile(optionalConfFile.toFile)
        else
          ConfigUtils.emptyConfig
      }

      val desktopConfig = ConfigFactory.parseResourcesAnySyntax("sc-desktop")

      fileConfig
        .withFallback(substitutionsConfig)
        .withFallback(autoParallelismConfig)
        .withFallback(desktopConfig)
        .withFallback(defaultConfig)
        .resolve()
    }

    serverAppConfig
  }
}
