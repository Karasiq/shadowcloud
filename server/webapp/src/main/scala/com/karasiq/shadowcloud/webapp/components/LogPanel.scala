package com.karasiq.shadowcloud.webapp.components

import com.karasiq.bootstrap.Bootstrap.default._
import org.scalajs.dom
import org.scalajs.dom.WebSocket
import org.scalajs.dom.raw.MessageEvent
import rx.Var
import scalaTags.all._

object LogPanel {
  def apply(): Tag = {
    val lines         = Var(Vector.empty[String])
    val url           = s"ws://${dom.window.location.host}/log"
    var ws: WebSocket = null
    initWebSocket()

    def initWebSocket(): Unit = {
      ws = new WebSocket(url)
      ws.onmessage = { (msg: MessageEvent) =>
        lines() = (msg.data.toString +: lines.now).take(200)
      }
      ws.onerror = { _ =>
        dom.window.setTimeout(initWebSocket _, 5000)
      }
    }

    GridSystem.containerFluid(
      Bootstrap.well(
        lines.map(_.mkString("\n")),
        whiteSpace.`pre-wrap`,
        overflow.auto
      )
    )
  }
}
