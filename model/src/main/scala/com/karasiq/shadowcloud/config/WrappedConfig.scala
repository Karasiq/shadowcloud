package com.karasiq.shadowcloud.config

import com.typesafe.config.Config

import com.karasiq.shadowcloud.model.SCEntity

trait WrappedConfig extends SCEntity {
  def rootConfig: Config
}

trait WrappedConfigFactory[T <: WrappedConfig] {
  def apply(config: Config): T
}
