import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import akka.util.Timeout
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.duration._
import scala.language.postfixOps

abstract class ActorSpec extends TestKit(ActorSystem("test")) with Suite with Matchers with ScalaFutures with BeforeAndAfterAll {
  implicit val actorMaterializer = ActorMaterializer()
  implicit val defaultTimeout = Timeout(10 seconds)

  override protected def afterAll() = {
    system.terminate()
    super.afterAll()
  }
}
