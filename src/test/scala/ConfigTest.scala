import com.karasiq.shadowcloud.config.AppConfig
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.language.postfixOps

class ConfigTest extends FlatSpec with Matchers {
  "Config" should "be loaded" in {
    val config = AppConfig(ConfigFactory.load().getConfig("shadowcloud"))
    config.index.syncInterval shouldBe (15 seconds)
  }
}
