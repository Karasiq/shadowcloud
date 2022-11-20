package com.karasiq.shadowcloud.metadata.utils

import java.io.InputStream

import akka.NotUsed
import akka.stream.scaladsl.{Flow, GraphDSL, Sink, Source, StreamConverters}
import akka.stream.{ActorAttributes, FlowShape}
import akka.util.ByteString
import com.karasiq.shadowcloud.actors.SCDispatchers
import com.karasiq.shadowcloud.metadata.{Metadata, MetadataParser}

import scala.concurrent.duration._
import scala.util.Try

/** Blocking InputStream API wrapper
  */
trait BlockingMetadataParser extends MetadataParser {
  protected def parseMetadata(name: String, mime: String, inputStream: InputStream): Source[Metadata, NotUsed]

  def parseMetadata(name: String, mime: String): Flow[ByteString, Metadata, NotUsed] = {
    val createInputStream = StreamConverters.asInputStream(10 seconds)
    val parseInputStream = Flow[InputStream]
      .flatMapConcat { inputStream ⇒
        parseMetadata(name, mime, inputStream)
          .alsoTo(Sink.onComplete(_ ⇒ Try(inputStream.close())))
      }
      .named("parseMetadataInputStream")
      .withAttributes(ActorAttributes.dispatcher(SCDispatchers.metadataBlocking))

    val blockingFlow = Flow.fromGraph(GraphDSL.create(createInputStream) { implicit builder ⇒ toStream ⇒
      import GraphDSL.Implicits._
      val parseStream = builder.add(parseInputStream)
      builder.materializedValue ~> parseStream
      FlowShape(toStream.in, parseStream.out)
    })

    blockingFlow
      .log("blocking-metadata-parse")
      .named("blockingParseMetadata")
      .mapMaterializedValue(_ ⇒ NotUsed)
  }
}
