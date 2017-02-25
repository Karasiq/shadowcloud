import com.karasiq.scalajsbundler.ScalaJSBundler.PageContent
import com.karasiq.scalajsbundler.dsl.{Script, _}
import sbt._

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
    val jQueryV = "1.12.0"
    val jsDeps = Seq(
      // jQuery
      Script from url("https://code.jquery.com/jquery-" + jQueryV + ".js"),

      // Bootstrap
      Style from url("https://raw.githubusercontent.com/twbs/bootstrap/v" + bootstrapV + "/dist/css/bootstrap.css"),
      Script from url("https://raw.githubusercontent.com/twbs/bootstrap/v" + bootstrapV + "/dist/js/bootstrap.js"),

      // Font Awesome
      Style from url("https://raw.githubusercontent.com/FortAwesome/Font-Awesome/v4.5.0/css/font-awesome.css")
    )

    val fonts = fontPackage("glyphicons-halflings-regular", "https://raw.githubusercontent.com/twbs/bootstrap/v" + bootstrapV + "/dist/fonts/glyphicons-halflings-regular") ++
      fontPackage("fontawesome-webfont", "https://raw.githubusercontent.com/FortAwesome/Font-Awesome/v4.5.0/fonts/fontawesome-webfont")
    jsDeps ++ fonts
  }

  def indexHtml: Seq[PageContent] = {
    Html from Assets.index
  }
}