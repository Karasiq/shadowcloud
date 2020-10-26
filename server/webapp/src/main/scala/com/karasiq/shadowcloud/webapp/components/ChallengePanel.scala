package com.karasiq.shadowcloud.webapp.components

import java.util.UUID

import akka.util.ByteString
import com.karasiq.bootstrap.Bootstrap.default._
import com.karasiq.shadowcloud.ui.Challenge
import com.karasiq.shadowcloud.webapp.api.AjaxApi
import com.karasiq.shadowcloud.webapp.components.common.Toastr
import com.karasiq.shadowcloud.webapp.context.AppContext
import com.karasiq.shadowcloud.webapp.utils.{Blobs, RxWithUpdate}
import org.scalajs.dom
import rx.{Rx, Var}
import scalaTags.all._

import scala.collection.mutable
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object ChallengePanel {
  def apply()(implicit context: AppContext): ChallengePanel = new ChallengePanel
}

class ChallengePanel(implicit context: AppContext) extends BootstrapHtmlComponent {
  private[this] val list = RxWithUpdate(Seq.empty[Challenge])(_ ⇒ AjaxApi.getChallenges())
  dom.window.setInterval(() ⇒ list.update(), 10000)

  override def renderTag(md: ModifierT*): TagT = {
    val map = mutable.HashMap.empty[UUID, TableRow]

    val source = list.toRx
    source.triggerLater {
      map.keys
        .filter(id ⇒ !source.now.exists(_.id == id))
        .foreach(map -= _)
    }

    def renderChallenge(challenge: Challenge): TableRow =
      map.getOrElseUpdate(
        challenge.id, {
          Toastr.info(challenge.title, "Challenge")
          val string = Var("")
          val files  = Var(Seq.empty[dom.File])

          TableRow(
            Seq(
              challenge.title,
              raw(challenge.html),
              FormInput.text("", string.reactiveInput),
              FormInput.file("", files.reactiveInputRead),
              Button(block = true)(
                context.locale.submit,
                onclick := Callback.onClick { _ ⇒
                  val data = files.now.headOption match {
                    case Some(value) ⇒
                      Blobs.toBytes(value)
                    case None ⇒
                      Future.successful(ByteString(string.now))
                  }

                  data
                    .flatMap(AjaxApi.solveChallenge(challenge.id, _))
                    .foreach(_ ⇒ list.update())
                }
              )
            )
          )
        }
      )

    Table(
      Rx(Seq(context.locale.name, context.locale.content, context.locale.pasteText, context.locale.file, context.locale.submit)),
      source.map(_.map(renderChallenge))
    )
  }
}
