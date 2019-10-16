package com.karasiq.shadowcloud.config

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.providers.LifecycleHook
import com.typesafe.config.Config

private[shadowcloud] final case class MiscConfig(rootConfig: Config, lifecycleHooks: Seq[Class[LifecycleHook]]) extends WrappedConfig

private[shadowcloud] object MiscConfig extends WrappedConfigFactory[MiscConfig] with ConfigImplicits {
  override def apply(config: Config): MiscConfig = {
    val hooks = config
      .getStrings("lifecycle-hooks")
      .map(className => Class.forName(className).asInstanceOf[Class[LifecycleHook]])

    MiscConfig(config, hooks)
  }
}
