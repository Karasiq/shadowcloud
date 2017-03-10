package com.karasiq.shadowcloud.config

import com.karasiq.shadowcloud.config.utils.ConfigImplicits

private[shadowcloud] case class ParallelismConfig(hashing: Int, encryption: Int, write: Int, read: Int)

private[shadowcloud] object ParallelismConfig extends ConfigImplicits {
  def apply(config: Config): ParallelismConfig = {
    ParallelismConfig(
      intOrAllCores(config, "hashing"),
      intOrAllCores(config, "encryption"),
      intOrAllCores(config, "write"),
      intOrAllCores(config, "read")
    )
  }

  private[this] def intOrAllCores(config: Config, path: String): Int = {
    val value = config.getInt(path)
    if (value > 0) value else sys.runtime.availableProcessors()
  }
}
