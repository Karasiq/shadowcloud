package com.karasiq.shadowcloud.config

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.ui.UIProvider
import com.karasiq.shadowcloud.ui.passwords.PasswordProvider
import com.typesafe.config.Config

private[shadowcloud] final case class UIConfig(rootConfig: Config, passwordProvider: Class[PasswordProvider], uiProvider: Class[UIProvider])
    extends WrappedConfig

private[shadowcloud] object UIConfig extends WrappedConfigFactory[UIConfig] with ConfigImplicits {
  override def apply(config: Config): UIConfig = {
    val passwordProvider: Class[PasswordProvider] = config.getClass("password-provider")
    val uiProvider: Class[UIProvider]             = config.getClass("ui-provider")
    UIConfig(config, passwordProvider, uiProvider)
  }
}
