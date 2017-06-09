package com.karasiq.shadowcloud.persistence.model

import java.util.UUID

import akka.util.ByteString

case class DBKey(id: UUID, forEncryption: Boolean, forDecryption: Boolean, key: ByteString)
