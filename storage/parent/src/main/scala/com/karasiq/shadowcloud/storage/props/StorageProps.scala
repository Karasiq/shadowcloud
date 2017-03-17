package com.karasiq.shadowcloud.storage.props

import java.net.URI
import java.nio.file.{Path => FSPath}

import com.karasiq.shadowcloud.config.SerializedProps
import com.karasiq.shadowcloud.storage.props.StorageProps._

import scala.language.postfixOps

case class StorageProps(storageType: String, address: Address = Address.empty,
                        credentials: Credentials = Credentials.empty, provider: String = "",
                        config: SerializedProps = SerializedProps.empty)

object StorageProps {
  // -----------------------------------------------------------------------
  // Sub-properties
  // -----------------------------------------------------------------------
  case class Address(address: URI = URI.create("file:///"), postfix: String = "default")
  object Address {
    val empty = Address()
  }

  case class Credentials(login: String = "", password: String = "") {
    override def toString: String = {
      s"Credentials($login:${"*" * password.length})"
    }
  }
  object Credentials {
    val empty = Credentials()
  }

  // -----------------------------------------------------------------------
  // Defaults
  // -----------------------------------------------------------------------
  def inMemory: StorageProps = {
    StorageProps("memory", Address(URI.create("memory:///")))
  }

  def fromDirectory(directory: FSPath): StorageProps = {
    StorageProps("files", Address(directory.toUri))
  }
}
