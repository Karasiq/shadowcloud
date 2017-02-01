package com.karasiq.shadowcloud.index

import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.EncryptionParameters

import scala.language.postfixOps

case class Chunk(size: Long, hash: ByteString, encryption: EncryptionParameters = EncryptionParameters.empty, encrypted: Option[Chunk] = None, data: ByteString = ByteString.empty)