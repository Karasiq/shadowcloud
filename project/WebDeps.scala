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
          meta(name := "viewport", content := "width=device-width, initial-scale=1.0"),
          link(rel := "icon", href := "favicon.png"),
          scalatags.Text.tags2.title("shadowcloud")
        ),
        body(
          // Empty
        )
      )
    }

    def style: String ="""
        |.glyphicon {
        |  margin-right: 3px;
        |};
      """.stripMargin
  }

  def bootstrap: Seq[PageContent] = {
    val bootstrapV = "3.3.7"
    val bootstrapDateV = "1.7.1"
    val jQueryV = "3.2.1"
    val fontAwesomeV = "4.7.0"

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

  def markedJS: Seq[PageContent] = {
    import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

    val highlightJSLanguages = Seq(
      "bash", "clojure", "coffeescript", "cpp", "cs", "d", "delphi", "erlang", "fsharp",
      "go", "groovy", "haskell", "java", "javascript", "json", "lua", "lisp", "markdown",
      "objectivec", "perl", "php", "python", "ruby", "rust", "scala", "scheme", "sql",
      "swift", "typescript", "css", "xml"
    )

    val highlightJSStyle = "github"
    
    val markedJS = "org.webjars.bower" % "marked" % "0.3.5"
    val highlightJS = "org.webjars" % "highlightjs" % "9.2.0"
    val tabOverrideJS = github("wjbryant", "taboverride", "4.0.3") / "build" / "output"

    val scripts = Seq(
      // Marked
      Script from markedJS / "marked.min.js",

      // Highlight.js
      Script from highlightJS / "highlight.min.js",
      Style from highlightJS / s"styles/$highlightJSStyle.css",

      // Tab Override
      Script from tabOverrideJS / "taboverride.min.js"
    )

    val highlightJSModules = for (lang ← highlightJSLanguages)
      yield Script from highlightJS / s"languages/$lang.min.js"

    scripts ++ highlightJSModules
  }

  def indexHtml: Seq[PageContent] = {
    Seq(Html from Assets.index, Style from Assets.style)
  }
}