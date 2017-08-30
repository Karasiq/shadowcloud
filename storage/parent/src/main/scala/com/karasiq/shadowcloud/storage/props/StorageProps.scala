package com.karasiq.shadowcloud.storage.props

import java.net.URI
import java.nio.file.{Path ⇒ FSPath}

import scala.language.postfixOps

import com.typesafe.config.Config

import com.karasiq.shadowcloud.config.{ConfigProps, WrappedConfig, WrappedConfigFactory}
import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.index.utils.HasEmpty
import com.karasiq.shadowcloud.storage.props.StorageProps.{Address, Credentials, Quota}
import com.karasiq.shadowcloud.utils.Utils

case class StorageProps(rootConfig: Config, storageType: String, address: Address = Address.empty,
                        credentials: Credentials = Credentials.empty, quota: Quota = Quota.empty,
                        provider: String = "") extends WrappedConfig

object StorageProps extends WrappedConfigFactory[StorageProps] with ConfigImplicits {
  // -----------------------------------------------------------------------
  // Sub-properties
  // -----------------------------------------------------------------------
  case class Address(rootConfig: Config, uri: Option[URI], namespace: String) extends WrappedConfig {
    override def toString: String = {
      s"Address(${uri.fold(namespace)(namespace + " at " + _)})"
    }
  }

  object Address extends WrappedConfigFactory[Address] {
    val empty = Address(Utils.emptyConfig)

    def apply(config: Config): Address = {
      Address(
        config,
        config.optional(_.getString("uri")).map(URI.create),
        config.withDefault("default", _.getString("namespace"))
      )
    }
  }

  case class Credentials(rootConfig: Config, login: String, password: String) extends WrappedConfig with HasEmpty {
    def isEmpty: Boolean = {
      login.isEmpty && password.isEmpty
    }

    override def toString: String = {
      if (isEmpty) {
        "Credentials.empty"
      } else {
        s"Credentials($login:******)"
      }
    }
  }

  object Credentials extends WrappedConfigFactory[Credentials] {
    val empty = Credentials(Utils.emptyConfig)

    def apply(config: Config): Credentials = {
      Credentials(
        config,
        config.withDefault("", _.getString("login")),
        config.withDefault("", _.getString("password"))
      )
    }
  }

  case class Quota(rootConfig: Config, limitSpace: Option[Long]) extends WrappedConfig with HasEmpty {
    def isEmpty: Boolean = limitSpace.isEmpty

    def getLimitedSpace(storageSpace: Long): Long = {
      limitSpace.fold(storageSpace)(math.min(storageSpace, _))
    }
  }

  object Quota extends WrappedConfigFactory[Quota] {
    val empty = Quota(Utils.emptyConfig)

    def apply(config: Config): Quota = {
      Quota(
        config,
        config.optional(_.getBytes("limit-space"))
      )
    }
  }

  // -----------------------------------------------------------------------
  // Constructor
  // -----------------------------------------------------------------------
  override def apply(config: Config): StorageProps = {
    StorageProps(
      config,
      config.getString("type"),
      Address(config.getConfigIfExists("address")),
      Credentials(config.getConfigIfExists("credentials")),
      Quota(config.getConfigIfExists("quota")),
      config.withDefault("", _.getString("provider"))
    )
  }

  // -----------------------------------------------------------------------
  // Defaults
  // -----------------------------------------------------------------------
  def inMemory: StorageProps = {
    StorageProps(ConfigProps.toConfig(ConfigProps("type" → "memory")))
  }

  def fromDirectory(directory: FSPath): StorageProps = {
    val config = ConfigProps.toConfig(ConfigProps("type" → "files", "address.uri" → directory.toUri.toString))
    StorageProps(config)
  }
}
