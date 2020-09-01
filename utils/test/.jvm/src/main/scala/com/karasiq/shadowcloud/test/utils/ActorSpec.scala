package com.karasiq.shadowcloud.test.utils

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, Suite}

import scala.concurrent.duration._

abstract class ActorSpec extends TestKit(ActorSystem("test")) with ImplicitSender
  with Suite with Matchers with ScalaFutures with BeforeAndAfterAll {

  override implicit def patienceConfig: PatienceConfig = {
    PatienceConfig(10 seconds, 100 millis)
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }
}

