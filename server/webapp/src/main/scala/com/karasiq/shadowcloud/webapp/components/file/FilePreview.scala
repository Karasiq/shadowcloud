package com.karasiq.shadowcloud.webapp.components.file

import scala.concurrent.Future
import scala.language.postfixOps
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import rx.Rx
import rx.async._

import com.karasiq.shadowcloud.index.File
import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.model.{FileId, RegionId}
import com.karasiq.shadowcloud.webapp.components.file.FilePreview.PreviewVariants
import com.karasiq.shadowcloud.webapp.components.metadata.MetadataView
import com.karasiq.shadowcloud.webapp.context.AppContext

object FilePreview {
  case class PreviewVariants(image: Option[Metadata.Thumbnail] = None,
                             text: Option[Metadata.Text] = None,
                             files: Option[Metadata.FileList] = None)

  def apply(regionId: RegionId, file: File)(implicit context: AppContext): FilePreview = {
    new FilePreview(regionId: RegionId, file)
  }

  private def getImagePreview(regionId: RegionId, fileId: FileId)(implicit context: AppContext): Future[PreviewVariants] = {
    val futureMetadata = context.api.getFileMetadata(regionId, fileId, Metadata.Tag.Disposition.PREVIEW)
    futureMetadata.map { metadatas ⇒
      println(metadatas)
      val image = metadatas.flatMap(_.value.thumbnail).headOption
      val text = {
        val texts = metadatas.flatMap(_.value.text)
        texts.find(_.format == "application/html").orElse(texts.find(_.format == "text/plain"))
      }
      val files = metadatas.flatMap(_.value.fileList).headOption
      PreviewVariants(image, text, files)
    }
  }
}

class FilePreview(regionId: RegionId, file: File)(implicit context: AppContext) extends BootstrapHtmlComponent {
  val previews = FilePreview.getImagePreview(regionId, file.id).toRx(PreviewVariants())

  def renderTag(md: ModifierT*): TagT = {
    val image = Rx(previews().image.map(MetadataView.thumbnail))
    val text = Rx(previews().text.filter(_.format == "text/plain").map(MetadataView.text))
    val files = Rx(previews().files.map(MetadataView.fileList))

    div(
      for (preview ← Seq(
        image,
        text,
        files
      )) yield preview.map(_.fold((): Frag)(GridSystem.mkRow(_)))
    )(md:_*)
  }
}


