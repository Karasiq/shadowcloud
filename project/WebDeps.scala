import sbt._

import com.karasiq.scalajsbundler.ScalaJSBundler.PageContent
import com.karasiq.scalajsbundler.dsl.{Script, _}

object WebDeps {
  private object Assets {
    def index: String = {
      import scalatags.Text.all._
      "<!DOCTYPE html>" + html(
        head(
          base(href := "/"),
          meta(name := "viewport", content := "width=device-width, initial-scale=1.0")
        ),
        body(
          // Empty
        )
      )
    }
  }

  def bootstrap: Seq[PageContent] = {
    val bootstrapV = "3.3.7"
    val bootstrapDateV = "1.7.1"
    val jQueryV = "3.2.1"
    val fontAwesomeV = "4.5.0"

    val jsDeps = Seq(
      // jQuery
      Script from url(s"https://code.jquery.com/jquery-$jQueryV.min.js"),

      // Bootstrap
      Style from url(s"https://raw.githubusercontent.com/twbs/bootstrap/v$bootstrapV/dist/css/bootstrap.css"),
      Script from url(s"https://raw.githubusercontent.com/twbs/bootstrap/v$bootstrapV/dist/js/bootstrap.js"),
      Style from url(s"https://cdn.jsdelivr.net/webjars/org.webjars/bootstrap-datepicker/$bootstrapDateV/css/bootstrap-datepicker3.min.css"),
      Script from url(s"https://cdn.jsdelivr.net/webjars/org.webjars/bootstrap-datepicker/$bootstrapDateV/js/bootstrap-datepicker.min.js"),

      // Font Awesome
      Style from url(s"https://raw.githubusercontent.com/FortAwesome/Font-Awesome/v$fontAwesomeV/css/font-awesome.css")
    )

    val fonts =
      fontPackage("glyphicons-halflings-regular", s"https://raw.githubusercontent.com/twbs/bootstrap/v$bootstrapV/dist/fonts/glyphicons-halflings-regular") ++
      fontPackage("fontawesome-webfont", s"https://raw.githubusercontent.com/FortAwesome/Font-Awesome/v$fontAwesomeV/fonts/fontawesome-webfont")

    jsDeps ++ fonts
  }

  def videoJS: Seq[PageContent] = {
    val videoJs = github("videojs", "video.js", "v5.8.0") / "dist"
    Seq(
      Script from url(videoJs % "video.min.js"),
      Style from url(videoJs % "video-js.min.css")
    )
  }

  def indexHtml: Seq[PageContent] = {
    Html from Assets.index
  }
}