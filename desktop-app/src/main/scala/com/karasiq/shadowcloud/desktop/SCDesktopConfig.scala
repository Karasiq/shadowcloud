package com.karasiq.shadowcloud.desktop

import java.io.File

import com.karasiq.common.configs.ConfigUtils
import com.typesafe.config.{Config, ConfigFactory}

object SCDesktopConfig {
  def load(): Config = {
    val autoParallelismConfig = {
      val cpuAvailable = Runtime.getRuntime.availableProcessors()
      val parallelism  = math.max(8, math.min(1, cpuAvailable / 2))
      ConfigFactory.parseString(s"shadowcloud.parallelism.default = $parallelism")
    }

    val substitutionsConfig = ConfigFactory.parseResourcesAnySyntax("sc-substitutions")

    val defaultConfig = ConfigFactory
      .defaultApplication()
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
      val files = (Seq(
        sys.props("user.home") + "/.shadowcloud/shadowcloud.conf",
        "shadowcloud.conf"
      ) ++ sys.props.get("shadowcloud.external-config")).map(new File(_)).filter(_.isFile)

      val fileConfig = files.foldLeft(ConfigUtils.emptyConfig) { (conf, f) ⇒
        ConfigFactory.parseFile(f).withFallback(conf)
      }

      val desktopConfig = ConfigFactory.parseResourcesAnySyntax("sc-desktop")

      ConfigFactory
        .defaultOverrides()
        .withFallback(fileConfig)
        .withFallback(substitutionsConfig)
        .withFallback(autoParallelismConfig)
        .withFallback(desktopConfig)
        .withFallback(defaultConfig)
        .resolve()
    }

    serverAppConfig
  }
}
