package com.karasiq.shadowcloud.index

import akka.util.ByteString

import scala.language.postfixOps

case class File(path: Path, name: String, size: Long, time: Long, key: ByteString = ByteString.empty, chunks: ByteString = ByteString.empty)
