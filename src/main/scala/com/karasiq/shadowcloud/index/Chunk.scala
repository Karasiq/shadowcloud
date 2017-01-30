package com.karasiq.shadowcloud.index

import akka.util.ByteString

import scala.language.postfixOps

case class Chunk(size: Long, hash: ByteString, key: ByteString = ByteString.empty, data: ByteString = ByteString.empty)