package com.karasiq.shadowcloud.config

import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigImplicits

private[shadowcloud] case class ParallelismConfig(rootConfig: Config,
                                                  query: Int,
                                                  hashing: Int, encryption: Int,
                                                  write: Int, read: Int) extends WrappedConfig

private[shadowcloud] object ParallelismConfig extends WrappedConfigFactory[ParallelismConfig] with ConfigImplicits {
  def apply(config: Config): ParallelismConfig = {
    def getPositiveInt(path: String, default: Int): Int = {
      config.optional(_.getInt(path))
        .filter(_ > 0)
        .getOrElse(default)
    }

    def getCores(path: String): Int = {
      getPositiveInt(path, sys.runtime.availableProcessors())
    }

    ParallelismConfig(
      config,
      getPositiveInt("query", 4),
      getCores("hashing"),
      getCores("encryption"),
      getPositiveInt("write", 4),
      getPositiveInt("read", 4)
    )
  }
}
