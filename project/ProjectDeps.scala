import sbt._

object ProjectDeps {
  type Deps = Seq[ModuleID]

  object akka {
    val version     = "2.6.6"
    val httpVersion = "10.1.11"

    def actors: Deps = Seq(
      "com.typesafe.akka" %% "akka-actor" % version
    )

    def streams: Deps = Seq(
      "com.typesafe.akka" %% "akka-stream" % version
    )

    def http: Deps = Seq(
      "com.typesafe.akka" %% "akka-http" % httpVersion
    )

    def persistence: Deps = Seq(
      "com.typesafe.akka" %% "akka-persistence" % version
    )

    def testKit: Deps = Seq(
      "com.typesafe.akka" %% "akka-testkit"        % version,
      "com.typesafe.akka" %% "akka-stream-testkit" % version,
      "com.typesafe.akka" %% "akka-http-testkit"   % httpVersion
    )

    def slf4j: Deps = Seq(
      "com.typesafe.akka" %% "akka-slf4j" % version
    )

    def all: Deps = {
      actors ++ streams ++ http ++ persistence // ++ testKit.map(_ % "test")
    }
  }

  def scalaTest: Deps = Seq(
    "org.scalatest" %% "scalatest" % "3.0.3"
  )

  def kryo: Deps = Seq(
    // "com.esotericsoftware" % "kryo" % "4.0.0",
    "com.twitter" %% "chill"      % "0.9.2",
    "com.twitter" %% "chill-akka" % "0.9.2"
  )

  def protobuf: Deps = {
    val version = scalapb.compiler.Version.scalapbVersion
    Seq(
      // "com.google.protobuf" % "protobuf-java" % "3.4.0",
      "com.thesamet.scalapb" %% "scalapb-runtime" % version,
      "com.thesamet.scalapb" %% "scalapb-runtime" % version % "protobuf"
    )
  }

  def autowire: Deps = Seq(
    "com.lihaoyi" %% "autowire" % "0.2.6"
  )

  def playJson: Deps = Seq(
    "com.typesafe.play" %% "play-json" % "2.6.7"
  )

  def boopickle: Deps = Seq(
    "io.suzaku" %% "boopickle" % "1.2.6"
  )

  def bouncyCastle: Deps = Seq(
    "org.bouncycastle" % "bcprov-jdk15on" % "1.60"
  )

  def libSodiumJni: Deps = Seq(
    "com.github.karasiq" % "kalium-fork" % "0.6.2" % "compile"
  )

  // http://h2database.com/html/main.html
  def h2: Deps = Seq(
    "com.h2database" % "h2"          % "1.4.192",
    "io.getquill"    %% "quill-jdbc" % "1.2.1"
  )

  // https://tika.apache.org/
  def tika: Deps = {
    val version = "1.22"
    Seq(
      "org.apache.tika" % "tika-parsers" % version,
      "org.apache.tika" % "tika-core"    % version,
      "org.xerial"      % "sqlite-jdbc"  % "3.45.1.0"
    )
  }

  // https://github.com/lz4/lz4-java
  def lz4: Deps = Seq(
    "org.lz4" % "lz4-java" % "1.4.0"
  )

  def scalafx: Deps = Seq(
    "org.scalafx" %% "scalafx" % "8.0.102-R11"
  )

  def commonsConfigs: Deps = Seq(
    "com.github.karasiq" %% "commons-configs" % "1.0.8"
  )

  def gdrive: Deps = Seq(
    "com.github.karasiq" %% "gdrive-api" % "1.1.1"
  )

  def mailrucloud: Deps = Seq(
    "com.github.karasiq" %% "mailrucloud-api" % "1.1.5"
  )

  def dropbox: Deps = Seq(
    "com.github.karasiq" %% "dropbox-api" % "1.0.1"
  )

  // https://github.com/lookfirst/sardine/
  def sardine: Deps = Seq(
    "com.github.lookfirst" % "sardine" % "5.9"
  )

  def logback: Deps = Seq(
    "ch.qos.logback" % "logback-classic" % "1.2.3"
  )

  object javacv {
    private val JavaCVVersion = "1.4.2"
    private val OpenCVVersion = "3.4.2"
    private val FFMpegVersion = "4.0.1"

    def isEnabled: Boolean = {
      sys.props.get("enable-javacv").exists(Set("1", "true").contains) || isFullEnabled
    }

    def isFullEnabled: Boolean = {
      sys.props.get("enable-javacv-all").exists(Set("1", "true").contains)
    }

    def main: Deps = {
      Seq("org.bytedeco" % "javacv" % JavaCVVersion)
    }

    def allPlatforms: Deps = {
      Seq("org.bytedeco" % "javacv-platform" % JavaCVVersion)
    }

    def mainPlatforms: Deps = {
      javaCvLibs(JavaCVVersion, Seq("windows", "linux"), Seq("x86", "x86_64"), "opencv" → OpenCVVersion, "ffmpeg" → FFMpegVersion) ++ javaCvLibs(
        JavaCVVersion,
        Seq("macosx"),
        Seq("x86_64"),
        "opencv" → OpenCVVersion,
        "ffmpeg" → FFMpegVersion
      )
    }

    def dockerPlatforms: Deps = {
      javaCvLibs(JavaCVVersion, Seq("linux"), Seq("x86_64"), "opencv" → OpenCVVersion, "ffmpeg" → FFMpegVersion)
    }

    def currentPlatform: Deps = {
      val platform = sys.props("os.name").toLowerCase match {
        case os if os.contains("win") ⇒ "windows"
        case os if os.contains("mac") ⇒ "macosx"
        case _                        ⇒ "linux"
      }

      javaCvLibs(
        JavaCVVersion,
        Seq(platform),
        if (platform == "macosx") Seq("x86_64") else Seq("x86", "x86_64"),
        "opencv" → OpenCVVersion,
        "ffmpeg" → FFMpegVersion
      )
    }

    private def javaCvLibs(javaCvVersion: String, platforms: Seq[String], architectures: Seq[String], libs: (String, String)*): Seq[ModuleID] = {
      // val platforms = Seq("windows", "linux")
      //val architectures = Seq("x86", "x86_64")

      (for {
        (lib, ver) ← libs
        os         ← platforms
        arch       ← architectures
      } yield Seq(
        // Add both: dependency and its native binaries for the current `platform`
        "org.bytedeco.javacpp-presets" % lib % s"$ver-$javaCvVersion",
        "org.bytedeco.javacpp-presets" % lib % s"$ver-$javaCvVersion" classifier s"$os-$arch"
      )).flatten
    }
  }

  def apacheCommonsIO: Deps = Seq(
    "commons-io" % "commons-io" % "2.6"
  )

  // https://github.com/vsch/flexmark-java
  def flexmark: Deps = Seq(
    "com.vladsch.flexmark" % "flexmark-all" % "0.28.4"
  )

  // https://github.com/Karasiq/webzinc
  def webzinc: Deps = {
    val version = "1.0.10"
    Seq(
      "net.sourceforge.htmlunit" % "htmlunit"          % "2.40.0",
      "com.github.karasiq"       %% "commons-network"  % "1.0.10",
      "com.github.karasiq"       %% "webzinc"          % version,
      "com.github.karasiq"       %% "webzinc-htmlunit" % version
    )
  }

  // https://github.com/SerCeMan/jnr-fuse
  def `jnr-fuse`: Deps = Seq(
    "com.github.serceman" % "jnr-fuse" % "0.5.4"
  )

  // https://github.com/xerial/larray
  def larray: Deps = Seq(
    "org.xerial.larray" %% "larray" % "0.4.0"
  )

  // https://github.com/Karasiq/scala-cache
  def larrayCache: Deps = larray ++ Seq(
    "com.github.karasiq" %% "scala-cache-larray" % "1.0.3"
  )

  def guava: Deps = Seq(
    "com.google.guava" % "guava" % "29.0-jre"
  )
}
