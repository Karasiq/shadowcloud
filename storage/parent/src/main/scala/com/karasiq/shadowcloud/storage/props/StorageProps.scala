package com.karasiq.shadowcloud.storage.props

import java.net.URI
import java.nio.file.{Path ⇒ FSPath}

import scala.language.postfixOps

import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.common.memory.MemorySize
import com.karasiq.shadowcloud.config.{ConfigProps, WrappedConfig, WrappedConfigFactory}
import com.karasiq.shadowcloud.index.utils.HasEmpty
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.storage.props.StorageProps.{Address, Credentials, Quota}
import com.karasiq.shadowcloud.utils.Utils

@SerialVersionUID(0L)
final case class StorageProps(rootConfig: Config, storageType: String, address: Address = Address.empty,
                              credentials: Credentials = Credentials.empty, quota: Quota = Quota.empty,
                              provider: String = "") extends WrappedConfig {

  require(storageType.nonEmpty, "Storage type should not be empty")

  override def toString = {
    "StorageProps" + (storageType, address, credentials, quota, provider).toString()
  }
}

object StorageProps extends WrappedConfigFactory[StorageProps] with ConfigImplicits {
  // -----------------------------------------------------------------------
  // Sub-properties
  // -----------------------------------------------------------------------
  @SerialVersionUID(0L)
  final case class Address(rootConfig: Config,
                           namespace: String,
                           uri: Option[URI],
                           path: Path) extends WrappedConfig {

    override def toString: String = {
      s"Address($namespace, ${uri.fold(path)(path + " at " + _)})"
    }
  }

  object Address extends WrappedConfigFactory[Address] {
    val empty = Address(Utils.emptyConfig)

    def apply(config: Config): Address = {
      Address(
        config,
        config.withDefault("default", _.getString("namespace")),
        config.optional(_.getString("uri")).map(URI.create),
        config.withDefault(Path.root, _.getString("path"))
      )
    }
  }

  @SerialVersionUID(0L)
  final case class Credentials(rootConfig: Config, login: String, password: String) extends WrappedConfig with HasEmpty {
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

  @SerialVersionUID(0L)
  final case class Quota(rootConfig: Config, limitSpace: Option[Long], useSpacePercent: Int) extends WrappedConfig with HasEmpty {
    require(limitSpace.forall(_ >= 0), "Limit space should be >= 0")
    require(useSpacePercent >= 0 && useSpacePercent <= 100, "Space percent should be between 0 and 100")

    def isEmpty: Boolean = limitSpace.isEmpty && useSpacePercent >= 100

    override def toString: String = {
      "Quota(" + useSpacePercent + "%" + limitSpace.fold("")(bs ⇒ " of " + MemorySize(bs)) + ")"
    }
  }

  object Quota extends WrappedConfigFactory[Quota] {
    val empty = Quota(Utils.emptyConfig)

    def apply(config: Config): Quota = {
      Quota(
        config,
        config.optional(_.getBytes("limit-space")),
        config.withDefault(100, _.getInt("use-space-percent"))
      )
    }

    def limitTotalSpace(quota: Quota, storageSpace: Long): Long = {
      val limitedSpace = quota.limitSpace.fold(storageSpace)(math.min(storageSpace, _))
      val percentSpace = if (quota.useSpacePercent == 100) limitedSpace else (limitedSpace * quota.useSpacePercent * 0.01).toLong
      percentSpace
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
