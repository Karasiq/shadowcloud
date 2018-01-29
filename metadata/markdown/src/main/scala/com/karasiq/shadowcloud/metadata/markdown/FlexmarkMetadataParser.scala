package com.karasiq.shadowcloud.metadata.markdown

import java.util

import akka.stream.scaladsl.Flow
import akka.util.ByteString
import com.typesafe.config.Config
import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.ext.wikilink.WikiLinkExtension

import com.karasiq.shadowcloud.metadata.{Metadata, MetadataParser}
import com.karasiq.shadowcloud.metadata.config.MetadataParserConfig
import com.karasiq.shadowcloud.streams.utils.ByteStreams

private[markdown] object FlexmarkMetadataParser {
  def apply(config: Config): FlexmarkMetadataParser = {
    new FlexmarkMetadataParser(config)
  }
}

private[markdown] class FlexmarkMetadataParser(config: Config) extends MetadataParser {
  import com.vladsch.flexmark.html.HtmlRenderer
  import com.vladsch.flexmark.parser.Parser
  import com.vladsch.flexmark.util.options.MutableDataSet

  protected val parserConfig = MetadataParserConfig(config)

  private[this] val options = {
    val options = new MutableDataSet
    options.set(Parser.EXTENSIONS, util.Arrays.asList(TablesExtension.create(), StrikethroughExtension.create(), TaskListExtension.create(), WikiLinkExtension.create(), AutolinkExtension.create()))
    options.set(HtmlRenderer.SOFT_BREAK, "<br />\n")
    options
  }

  private[this] val parser = Parser.builder(options).build
  private[this] val renderer = HtmlRenderer.builder(options).build

  def canParse(name: String, mime: String) = {
    parserConfig.canParse(name, mime)
  }

  def parseMetadata(name: String, mime: String) = {
    Flow[ByteString]
      .via(ByteStreams.concat)
      .map { bytes ⇒
        val markdownStr = bytes.utf8String
        val html = renderer.render(parser.parse(markdownStr))
        Metadata(Some(Metadata.Tag("markdown", "flexmark", Metadata.Tag.Disposition.PREVIEW)), Metadata.Value.Text(Metadata.Text("text/html", html)))
      }
      .named("flexmarkParse")
  }
}
