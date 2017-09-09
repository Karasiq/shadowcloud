package com.karasiq.shadowcloud.model.utils

import com.karasiq.shadowcloud.model.SequenceNr

sealed trait IndexScope

object IndexScope {
  case object Current extends IndexScope
  case object Persisted extends IndexScope
  final case class UntilSequenceNr(sequenceNr: SequenceNr) extends IndexScope
  final case class UntilTime(timestamp: Long) extends IndexScope

  def default = Current
}