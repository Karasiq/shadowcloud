package com.karasiq.shadowcloud.actors.messages

import scala.language.postfixOps

case class StorageEnvelope(storageId: String, message: Any)
