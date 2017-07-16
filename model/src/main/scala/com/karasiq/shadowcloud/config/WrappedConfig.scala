package com.karasiq.shadowcloud.config

import com.typesafe.config.Config

trait WrappedConfig {
  def rootConfig: Config
}

trait WrappedConfigFactory[T <: WrappedConfig] {
  def apply(config: Config): T
}