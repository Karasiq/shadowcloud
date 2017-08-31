package com.karasiq.shadowcloud.providers

import akka.NotUsed
import akka.stream.FlowShape
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge}
import akka.util.ByteString

import com.karasiq.shadowcloud.config.ProvidersConfig
import com.karasiq.shadowcloud.metadata._
import com.karasiq.shadowcloud.utils.ProviderInstantiator

private[shadowcloud] trait MetadataModuleRegistry extends MimeDetector with MetadataParser {
  def metadataPlugins: Set[String]
}

private[shadowcloud] object MetadataModuleRegistry {
  def apply(providers: ProvidersConfig[MetadataProvider])(implicit inst: ProviderInstantiator): MetadataModuleRegistry = {
    new MetadataModuleRegistryImpl(providers)
  }
}

private[shadowcloud] final class MetadataModuleRegistryImpl(providers: ProvidersConfig[MetadataProvider])
                                                           (implicit inst: ProviderInstantiator) extends MetadataModuleRegistry {

  private[this] val (plugins, detectors, parsers) = {
    val (detectors, parsers) = providers.instances
      .map(kv ⇒ (kv._2.detectors, kv._2.parsers))
      .unzip
    (providers.classes.map(_._1), detectors.flatten, parsers.flatten)
  }

  val metadataPlugins: Set[String] = plugins.toSet

  def getMimeType(name: String, data: ByteString): Option[String] = {
    detectors.iterator
      .map(_.getMimeType(name, data).filterNot(mime ⇒ mime.isEmpty || mime == MimeDetector.DefaultMime))
      .find(_.nonEmpty)
      .flatten
  }

  def canParse(name: String, mime: String): Boolean = {
    parsers.exists(_.canParse(name, mime))
  }

  def parseMetadata(name: String, mime: String): Flow[ByteString, Metadata, NotUsed] = {
    val availableParsers = parsers.filter(_.canParse(name, mime))
    // println(s"$name $mime -> $availableParsers")
    val graph = GraphDSL.create() { implicit builder ⇒
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[ByteString](availableParsers.length))
      val merge = builder.add(Merge[Metadata](availableParsers.length))
      availableParsers.foreach { parser ⇒
        val parse = builder.add(parser.parseMetadata(name, mime).async)
        broadcast ~> parse ~> merge
      }
      FlowShape(broadcast.in, merge.out)
    }
    Flow.fromGraph(graph).named("parseMetadata")
  }
}
