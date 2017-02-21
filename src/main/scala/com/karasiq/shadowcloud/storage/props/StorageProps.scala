package com.karasiq.shadowcloud.storage.props

import java.net.URI

import com.karasiq.shadowcloud.storage.props.StorageProps._

import scala.language.postfixOps

case class StorageProps(storageType: String, address: Address = Address.empty, credentials: Credentials = Credentials.empty)

object StorageProps {
  case class Address(address: URI = URI.create("file:///"), postfix: String = "default")
  object Address {
    val empty = Address()
  }

  case class Credentials(login: String = "", password: String = "")
  object Credentials {
    val empty = Credentials()
  }
}
