package com.karasiq.shadowcloud.api

import scala.language.higherKinds

import akka.util.ByteString

trait SCApiClient[Value, Reader[_], Writer[_]] extends autowire.Client[Value, Reader, Writer] with SCApiMeta {
  def encodePayload(value: Map[String, Value]): ByteString
}
