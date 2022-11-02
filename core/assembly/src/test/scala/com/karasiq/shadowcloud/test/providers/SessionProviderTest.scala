package com.karasiq.shadowcloud.test.providers

import org.scalatest.FlatSpecLike
import org.scalatest.exceptions.TestFailedException

import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.test.utils.{SCExtensionSpec, TestUtils}

class SessionProviderTest extends SCExtensionSpec with FlatSpecLike {
  val storageId = "testStorage"
  val testKey   = "test_session"
  val testData  = TestUtils.randomBytes(10)
  val testValue = TestUtils.testDiff

  "Session provider" should "create session" in {
    sc.sessions.provider.storeSession(storageId, testKey, testData).futureValue
    sc.sessions.provider.getSessions(storageId).futureValue shouldBe Set(testKey)
    sc.sessions.provider.loadSession(storageId, testKey).futureValue shouldBe testData
  }

  it should "drop session" in {
    sc.sessions.provider.dropSession(storageId, testKey).futureValue
    sc.sessions.provider.getSessions(storageId).futureValue shouldBe empty
    intercept[TestFailedException](sc.sessions.provider.loadSession(storageId, testKey).futureValue)
  }

  it should "drop all sessions" in {
    sc.sessions.provider.storeSession(storageId, testKey, testData).futureValue
    sc.sessions.provider.dropSessions(storageId).futureValue
    sc.sessions.provider.getSessions(storageId).futureValue shouldBe empty
    intercept[TestFailedException](sc.sessions.provider.loadSession(storageId, testKey).futureValue)
  }

  "Sessions util" should "store serialized object" in {
    sc.sessions.set(storageId, testKey, testValue).futureValue
    sc.sessions.get[IndexDiff](storageId, testKey).futureValue shouldBe testValue
  }
}
