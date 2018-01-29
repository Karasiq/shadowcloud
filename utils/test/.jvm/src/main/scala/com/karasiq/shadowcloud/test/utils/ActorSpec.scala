package com.karasiq.shadowcloud.test.utils

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, Suite}
import org.scalatest.concurrent.ScalaFutures

abstract class ActorSpec extends TestKit(ActorSystem("test")) with ImplicitSender
  with Suite with Matchers with ScalaFutures with BeforeAndAfterAll {

  override implicit def patienceConfig: PatienceConfig = {
    PatienceConfig(5 seconds, 50 millis)
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }
}

