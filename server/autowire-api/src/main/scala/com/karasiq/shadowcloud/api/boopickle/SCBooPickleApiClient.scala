package com.karasiq.shadowcloud.api.boopickle

import akka.util.ByteString
import boopickle.Pickler

import com.karasiq.shadowcloud.api.SCApiClient

trait SCBooPickleApiClient extends SCApiClient[ByteString, Pickler, Pickler] with SCBooPickleApi {
  import encoding.implicits._

  def encodePayload(value: Map[String, ByteString])  = encoding.encode(value)
  def read[Result: boopickle.Pickler](p: ByteString) = encoding.decode[Result](p)
  def write[Result: boopickle.Pickler](r: Result)    = encoding.encode(r)
}
