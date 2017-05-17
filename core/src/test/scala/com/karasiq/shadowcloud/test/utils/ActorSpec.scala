package com.karasiq.shadowcloud.test.utils

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

import com.karasiq.shadowcloud.ShadowCloud

abstract class ActorSpec extends TestKit(ActorSystem("test")) with ImplicitSender
  with Suite with Matchers with ScalaFutures with BeforeAndAfterAll with TestImplicits {

  val sc = ShadowCloud(system)
  implicit val defaultTimeout = Timeout(15 seconds)
  implicit val materializer = sc.implicits.materializer
  implicit val executionContext = sc.implicits.executionContext

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }
}
