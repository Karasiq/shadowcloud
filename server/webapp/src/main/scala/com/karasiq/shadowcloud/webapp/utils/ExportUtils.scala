package com.karasiq.shadowcloud.webapp.utils

import play.api.libs.json.Json

import com.karasiq.shadowcloud.api.json.SCJsonEncoders
import com.karasiq.shadowcloud.model.File
import com.karasiq.shadowcloud.model.keys.KeySet

object ExportUtils {
  private[this] object JsonEncoders extends SCJsonEncoders
  import JsonEncoders._

  def encodeKey(key: KeySet): String = {
    Json.prettyPrint(Json.toJson(key))
  }

  def decodeKey(key: String): KeySet = {
    Json.parse(key).as[KeySet]
  }

  def encodeFile(file: File): String = {
    Json.prettyPrint(Json.toJson(file))
  }
}
