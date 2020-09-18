package com.karasiq.shadowcloud.metadata.markdown

import java.net.URI

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.shadowcloud.metadata.Metadata.Tag
import com.karasiq.shadowcloud.metadata.config.MetadataParserConfig
import com.karasiq.shadowcloud.metadata.{Metadata, MetadataParser}
import com.karasiq.shadowcloud.streams.utils.ByteStreams
import com.typesafe.config.Config
import org.jsoup.Jsoup

import scala.collection.JavaConverters._
import scala.util.Try

object HtmlMetadataParser {
  def apply(config: Config): HtmlMetadataParser = new HtmlMetadataParser(config)
}

class HtmlMetadataParser(config: Config) extends MetadataParser {
  protected object settings {
    val parserConfig   = MetadataParserConfig(config)
    val sizeLimit      = config.getBytesInt("size-limit")
    val removeElements = config.getStringList("remove-elements").asScala
    val allowedHosts   = config.getStringList("img-allowed-hosts").asScala
  }

  def canParse(name: String, mime: String): Boolean = {
    settings.parserConfig.canParse(name, mime)
  }

  def parseMetadata(name: String, mime: String): Flow[ByteString, Metadata, NotUsed] = {
    Flow[ByteString]
      .via(ByteStreams.limit(settings.sizeLimit))
      .via(ByteStreams.concat)
      .filter(_.nonEmpty)
      .map { bytes ⇒
        val html = Jsoup.parse(bytes.utf8String).body()

        html
          .select("img, video")
          .asScala
          .filterNot { e ⇒
            val srcHost = Option(e.attr("src"))
              .flatMap(url ⇒ Try(new URI(url).getHost).toOption)

            srcHost exists { host ⇒
              settings.allowedHosts.exists(ah ⇒ host == ah || host.endsWith("." + ah))
            }
          }
          .foreach(_.remove())

        html
          .select(settings.removeElements.mkString(", "))
          .asScala
          .foreach(_.remove())

        val result = html.toString
        Metadata(Some(Tag("markdown", "html", Tag.Disposition.PREVIEW)), Metadata.Value.Text(Metadata.Text("text/html", result)))
      }
      .named("htmlSanitize")
  }
}
