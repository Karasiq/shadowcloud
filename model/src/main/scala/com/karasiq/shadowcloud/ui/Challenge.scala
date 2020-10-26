package com.karasiq.shadowcloud.ui

import java.time.Instant
import java.util.UUID

import com.karasiq.shadowcloud.model.SCEntity
import com.karasiq.shadowcloud.ui.Challenge.AnswerFormat

@SerialVersionUID(0L)
final case class Challenge(id: UUID, time: Instant, title: String, html: String, answerFormat: AnswerFormat) extends SCEntity

object Challenge {
  sealed trait AnswerFormat
  object AnswerFormat {
    case object Ack    extends AnswerFormat
    case object String extends AnswerFormat
    case object Binary extends AnswerFormat
  }
}
