package com.karasiq.shadowcloud.server.http

import java.io.{PrintWriter, StringWriter}
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging.{Error, Info, LogEvent, LogEventWithCause, Warning}
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.Attributes.LogLevels
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}

trait SCWebSocketRoutes { self: SCAkkaHttpApiRoutes with SCHttpServerSettings with Directives ⇒
  def createLogStream(implicit as: ActorSystem): Source[String, NotUsed] = {
    val formatter = DateTimeFormatter.ISO_TIME.withZone(ZoneId.systemDefault())

    def formatTime(ts: Long) = {
      val instant = Instant.ofEpochMilli(ts)
      formatter.format(instant)
    }

    Source
      .actorRef[LogEvent](PartialFunction.empty, PartialFunction.empty, 100, OverflowStrategy.dropBuffer)
      .mapMaterializedValue { actor =>
        Seq(classOf[Info], classOf[Warning], classOf[Error]).foreach(as.eventStream.subscribe(actor, _))
        NotUsed
      }
      .filterNot(_.level == LogLevels.Debug)
      .collect {
        case e: LogEventWithCause if e.cause != null =>
          val stack = {
            val sw = new StringWriter()
            val pw = new PrintWriter(sw)
            e.cause.printStackTrace(pw)
            pw.close()
            sw.toString
          }
          s"${formatTime(e.timestamp)} (${e.logSource}) - ${e.message}\n$stack"

        case e: LogEvent =>
          s"${formatTime(e.timestamp)} (${e.logSource}) - ${e.message}"
      }
  }

  def scWebSocketRoutes: Route = path("log") {
    extractActorSystem { implicit as =>
      val flow = Flow.fromSinkAndSource(Sink.ignore, createLogStream).map(TextMessage(_))
      handleWebSocketMessages(flow)
    }
  }
}
