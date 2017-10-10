package com.karasiq.shadowcloud.webapp.components

import play.api.libs.json.Json
import rx.{Ctx, Rx, Var}

import com.karasiq.shadowcloud.api.json.SCJsonEncoders
import com.karasiq.shadowcloud.model.{Path, RegionId}

private[webapp] object SCContextBinding {
  def apply()(implicit ctxOwner: Ctx.Owner): SCContextBinding = {
    new SCContextBinding()
  }

  final case class EncodedContext(regionId: Option[RegionId], folder: Path)

  private object Encoders extends SCJsonEncoders {
    implicit val encodedContextFormat = Json.format[EncodedContext]
  }
}

private[webapp] class SCContextBinding()(implicit ctxOwner: Ctx.Owner) {
  import SCContextBinding._
  import Encoders._

  val context = Var(EncodedContext(None, Path.root))

  def bindToFrontend(sf: SCFrontend): Unit = {
    sf.regionSwitcher.regionSelector.selectedRegion.foreach { regionId ⇒
      context() = context.now.copy(regionId = regionId)
    }

    sf.folderContextRx.foreach(_.foreach { fc ⇒
      fc.selected() = context.now.folder
      fc.selected.triggerLater(context() = context.now.copy(folder = fc.selected.now))
    })

    context.triggerLater {
      sf.regionSwitcher.regionSelector.selectedRegion() = context.now.regionId
      sf.folderContextRx.now.foreach(_.selected() = context.now.folder)
    }
  }

  def bindToString(value: Var[Option[String]]): Unit = {
    value.foreach {
      case Some(encoded) if encoded.nonEmpty ⇒
        val encodedContext = Json.parse(encoded).as[EncodedContext]
        context() = encodedContext

      case _ ⇒
        // Ignore
    }

    context.triggerLater {
      val encoded = Json.toJson(context.now).toString()
      value() = Some(encoded)
    }
  }

  def toTitleRx: Rx[String] = {
    context.map(c ⇒ c.regionId.fold(c.folder.toString)(_ + " - " + c.folder))
  }
}
