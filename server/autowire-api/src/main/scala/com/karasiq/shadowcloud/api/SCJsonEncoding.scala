package com.karasiq.shadowcloud.api

import play.api.libs.json._

import com.karasiq.shadowcloud.index.Path

object SCJsonEncoding extends SCApiEncoding {
  def encodePath(path: Path): String = {
    Json.stringify(Json.toJson(path.nodes))
  }

  def decodePath(json: String): Path = {
    Path(Json.fromJson[Seq[String]](Json.parse(json)).get)
  }
}
