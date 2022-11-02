package com.karasiq.shadowcloud.api

import scala.language.higherKinds

import akka.util.ByteString

trait SCApiServer[Value, Reader[_], Writer[_]] extends autowire.Server[Value, Reader, Writer] with SCApiMeta {
  def decodePayload(payload: ByteString): Map[String, Value]
}
