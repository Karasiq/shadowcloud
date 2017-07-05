package com.karasiq.shadowcloud.metadata

import java.io.File
import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.util.ByteString
import org.apache.commons.io.{FilenameUtils, FileUtils}

import com.karasiq.shadowcloud.ShadowCloud

object MetadataParserApp extends App {
  val actorSystem = ActorSystem("metadata-parser")
  val sc = ShadowCloud(actorSystem)
  val parser = sc.modules.metadata

  if (args.isEmpty) sys.error("No files specified.")
  args.foreach { file ⇒
    val bytes = ByteString(FileUtils.readFileToByteArray(new File(file)))
    val mime = parser.getMimeType(file, bytes)
      .getOrElse("application/octet-stream")
    println(s"File: $file")
    println(s"Mime type: $mime")

    val metadata = parser.parseMetadata(file, mime, bytes)
    metadata.foreach { value ⇒
      val fileName = FilenameUtils.getName(file) + "_" + value.tag.fold("unk")(t ⇒ s"${t.parser}_${t.id}") + ".txt"
      FileUtils.writeStringToFile(new File(fileName), value.toString, StandardCharsets.UTF_8)
      println(s"Metadata saved: $fileName")
    }
  }

  actorSystem.terminate()
}
