package com.karasiq.shadowcloud.api.boopickle

import akka.util.ByteString
import boopickle.Pickler

import com.karasiq.shadowcloud.api.SCApiServer

trait SCBooPickleApiServer extends SCApiServer[ByteString, Pickler, Pickler] with SCBooPickleApi {
  import encoding.implicits._

  def decodePayload(payload: ByteString) = {
    encoding.decode[Map[String, ByteString]](payload)
  }

  def read[Result: boopickle.Pickler](p: ByteString) = {
    encoding.decode[Result](p)
  }

  def write[Result: boopickle.Pickler](r: Result) = {
    encoding.encode(r)
  }
}
