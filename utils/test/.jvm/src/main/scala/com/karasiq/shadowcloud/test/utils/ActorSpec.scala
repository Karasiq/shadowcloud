package com.karasiq.shadowcloud.test.utils

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, Matchers, Suite}
import org.scalatest.concurrent.ScalaFutures

abstract class ActorSpec extends TestKit(ActorSystem("test")) with ImplicitSender
  with Suite with Matchers with ScalaFutures with BeforeAndAfterAll with TestImplicits {

  implicit val defaultTimeout = Timeout(15 seconds)
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }
}

