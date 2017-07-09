package com.karasiq.shadowcloud.metadata.utils

import java.io.InputStream

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.NotUsed
import akka.stream.{ActorAttributes, Attributes, FlowShape}
import akka.stream.scaladsl.{Flow, GraphDSL, Sink, Source, StreamConverters}
import akka.util.ByteString

import com.karasiq.shadowcloud.metadata.{Metadata, MetadataParser}

/**
  * Blocking InputStream API wrapper
  */
trait BlockingMetadataParser extends MetadataParser {
  protected def parseMetadata(name: String, mime: String, inputStream: InputStream): Source[Metadata, NotUsed]

  def parseMetadata(name: String, mime: String): Flow[ByteString, Metadata, NotUsed] = {
    val createInputStream = StreamConverters.asInputStream(15 seconds)
    val parseInputStream = Flow[InputStream].flatMapConcat { inputStream ⇒
      parseMetadata(name, mime, inputStream)
        .alsoTo(Sink.onComplete(_ ⇒ inputStream.close()))
    }

    val blockingFlow = Flow.fromGraph(GraphDSL.create(createInputStream) { implicit builder ⇒ toStream ⇒
      import GraphDSL.Implicits._
      val parseStream = builder.add(parseInputStream)
      builder.materializedValue ~> parseStream
      FlowShape(toStream.in, parseStream.out)
    })

    blockingFlow
      .addAttributes(Attributes.name("blockingMetadataParser") and ActorAttributes.IODispatcher)
      .mapMaterializedValue(_ ⇒ NotUsed)
  }
}
