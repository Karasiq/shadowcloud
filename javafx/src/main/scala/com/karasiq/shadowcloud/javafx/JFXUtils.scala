package com.karasiq.shadowcloud.javafx

import scalafx.application.Platform

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}

private[javafx] object JFXUtils {
  def getResourcePath(fileName: String): String = {
    getClass.getClassLoader.getResource(fileName).toString
  }

  def runNow[T](f: ⇒ T): T = {
    val promise = Promise[T]
    Platform.runLater {
      try promise.success(f)
      catch { case err: Throwable ⇒ promise.failure(err) }
    }
    Await.result(promise.future, Duration.Inf)
  }
}
