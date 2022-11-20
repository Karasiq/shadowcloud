package com.karasiq.shadowcloud.metadata

import java.io.File
import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import org.apache.commons.io.{FilenameUtils, FileUtils}

import com.karasiq.common.memory.MemorySize
import com.karasiq.shadowcloud.ShadowCloud

object MetadataParserApp extends App {
  val actorSystem = ActorSystem("metadata-parser")
  val sc          = ShadowCloud(actorSystem)
  val parser      = sc.modules.metadata
  import sc.implicits._

  if (args.isEmpty) sys.error("No files specified.")
  Source(args.toVector)
    .flatMapConcat { file ⇒
      val mime = parser
        .getMimeType(file, ByteString(FileUtils.readFileToByteArray(new File(file))))
        .getOrElse(MimeDetector.DefaultMime)
      println(s"File: $file")
      println(s"Mime type: $mime")

      FileIO
        .fromPath(Paths.get(file))
        .via(parser.parseMetadata(file, mime))
        .alsoTo(Sink.foreach { value ⇒
          val (extension, bytes) = value.value match {
            case Metadata.Value.Text(text) ⇒
              (text.format, ByteString(text.data))

            case Metadata.Value.FileList(files) ⇒
              ("files.txt", ByteString(files.files.map(f ⇒ s"${f.path} (${MemorySize(f.size)})").mkString("\n")))

            case Metadata.Value.ImageData(data) ⇒
              ("imgdata.txt", ByteString(data.toString))

            case Metadata.Value.Thumbnail(thumbnail) ⇒
              (thumbnail.format, thumbnail.data)

            case Metadata.Value.Table(table) ⇒
              ("table.txt", ByteString(table.values.map { case (key, vs) ⇒ s"$key: ${vs.values.mkString(", ")}" }.mkString("\n")))

            case Metadata.Value.EmbeddedResources(resources) ⇒
              ("embedded.txt", ByteString(resources.toString))

            case unknown ⇒
              (s"${unknown.getClass.getSimpleName}.txt", ByteString(value.toString))
          }
          val fileName = FilenameUtils.getName(file) + "_" + value.tag
            .fold("unk")(t ⇒ s"${t.plugin}_${t.parser}_${t.disposition.toString().toLowerCase}.$extension")
          FileUtils.writeByteArrayToFile(new File(fileName), bytes.toArray)
          println(s"Metadata saved: $fileName")
        })
    }
    .runWith(Sink.onComplete(_ ⇒ actorSystem.terminate()))
}
