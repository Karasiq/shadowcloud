package com.karasiq.shadowcloud.ui

import java.time.Instant
import java.util.UUID

import akka.actor.ActorSystem
import akka.event.Logging
import akka.util.ByteString
import com.karasiq.shadowcloud.ui.Challenge.AnswerFormat

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise, TimeoutException}

class ChallengeHub(implicit as: ActorSystem) {
  import as.dispatcher
  private[this] val log        = Logging(as, classOf[ChallengeHub])
  private[this] val challenges = TrieMap.empty[UUID, (Challenge, Promise[ByteString], Deadline)]
  private[this] val schedule = as.scheduler.scheduleAtFixedRate(1 second, 1 second) { () ⇒
    challenges.values.collect {
      case (challenge, promise, deadline) if deadline.isOverdue() ⇒
        log.warning("Challenge timed out: {}", challenge.title)
        promise.tryFailure(new TimeoutException(s"Challenge timed out: ${challenge.title}"))
    }
  }

  def list(): Seq[Challenge] =
    challenges.values.map(_._1).toVector.sortBy(_.time.toEpochMilli)

  def create(
      title: String,
      html: String = "",
      answerFormat: AnswerFormat = AnswerFormat.String,
      deadline: FiniteDuration = 5 minutes
  ): Future[ByteString] = {
    val challenge = Challenge(UUID.randomUUID(), Instant.now(), title, html, answerFormat)
    val promise   = Promise[ByteString]
    log.info("Challenge created: {}", title)
    challenges(challenge.id) = (challenge, promise, Deadline.now + deadline)
    promise.future.onComplete(_ ⇒ challenges -= challenge.id)
    promise.future
  }

  def solve(id: UUID, response: ByteString = ByteString.empty): Unit = challenges.remove(id).foreach {
    case (challenge, promise, _) ⇒
      log.info("Challenge solved: {}", challenge.title)
      promise.trySuccess(response)
  }

  override def finalize(): Unit = {
    schedule.cancel()
    challenges.values.foreach { case (_, p, _) ⇒ p.tryFailure(new RuntimeException("Challenge hub closed")) }
    super.finalize()
  }
}
