package com.karasiq.shadowcloud.metadata.tika

import java.io.InputStream

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.NotUsed
import akka.stream.FlowShape
import akka.stream.scaladsl.{Flow, GraphDSL, StreamConverters}
import akka.util.ByteString
import com.typesafe.config.Config
import org.apache.commons.io.FilenameUtils
import org.apache.tika.metadata.{TikaCoreProperties, Metadata ⇒ TikaMetadata}
import org.apache.tika.parser.{ParseContext, Parser}

import com.karasiq.shadowcloud.metadata.{Metadata, MetadataParser}

/**
  * Abstract metadata parser
  */
private[tika] trait TikaMetadataParser extends MetadataParser {
  val parser: Parser
  val config: Config

  protected lazy val extensions = config.getStringList("extensions").asScala.toSet
  protected lazy val mimes = {
    val configMimes = config.getStringList("mimes").asScala.toSet
    if (config.getBoolean("handle-all-mimes")) {
      val internalList = parser.getSupportedTypes(new ParseContext).asScala.map(_.toString)
      internalList ++ configMimes
    } else {
      configMimes
    }
  }

  protected def parseStream(metadata: TikaMetadata, inputStream: InputStream): Seq[Metadata]

  def canParse(name: String, mime: String): Boolean = {
    mimes.contains(mime) || extensions.contains(FilenameUtils.getExtension(name))
  }


  def parseMetadata(name: String, mime: String): Flow[ByteString, Metadata, NotUsed] = {
    val createInputStream = StreamConverters.asInputStream(15 seconds)
    val parseInputStream = Flow[InputStream].mapConcat { inputStream ⇒
      val metadata = new TikaMetadata
      metadata.add(TikaCoreProperties.ORIGINAL_RESOURCE_NAME, name)
      metadata.add(TikaCoreProperties.CONTENT_TYPE_HINT, mime)
      try {
        parseStream(metadata, inputStream).toVector
      } finally {
        inputStream.close()
      }
    }

    Flow.fromGraph(GraphDSL.create(createInputStream) { implicit builder ⇒ toStream ⇒
      import GraphDSL.Implicits._
      val parseStream = builder.add(parseInputStream)
      builder.materializedValue ~> parseStream
      FlowShape(toStream.in, parseStream.out)
    }).mapMaterializedValue(_ ⇒ NotUsed)
  }
}