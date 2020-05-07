import com.karasiq.scalajsbundler.ScalaJSBundler.PageContent
import com.karasiq.scalajsbundler.dsl.{Script, _}
import sbt._

private object JSModuleIDBuilder // Implicit fix

object WebDeps {
  private object Assets {
    def index: String = {
      import scalatags.Text.all._
      "<!DOCTYPE html>" + html(
        head(
          base(href := "/"),
          meta(name := "viewport", content := "width=device-width, initial-scale=1.0"),
          link(rel := "icon", href := "favicon.png"),
          link(rel := "stylesheet", id := "sc-theme", href := "themes/default.css"),
          scalatags.Text.tags2.title("shadowcloud")
        ),
        body(
          // Empty
        )
      )
    }

    def style: String = """
        |.glyphicon {
        |  margin-right: 3px;
        |}
        |
        |.sc-main-container {
        |  margin-bottom: 100px;
        |}
      """.stripMargin
  }

  private object Bootstrap {
    private[this] val BootstrapV           = "3.3.7"
    private[this] val BootstrapDatePickerV = "1.7.1"
    private[this] val JQueryV              = "3.2.1"
    private[this] val FontAwesomeV         = "4.7.0"

    /* private def themes = Seq(
      "Cerulean", "Cosmo", "Cyborg", "Darkly", "Flatly", "Journal", "Lumen", "Paper", "Readable",
      "Sandstone", "Simplex", "Slate", "Spacelab", "Superhero", "United", "Yeti"
    ) */

    // def defaultTheme = sys.props.getOrElse("bootstrap-theme", "cerulean")

    def themeCss(theme: String) =
      Style from url(s"https://bootswatch.com/3/${theme.toLowerCase}/bootstrap.min.css")

    def scripts: Seq[PageContent] = {
      Seq(
        Script from url(s"https://code.jquery.com/jquery-$JQueryV.min.js"),
        Script from url(s"https://raw.githubusercontent.com/twbs/bootstrap/v$BootstrapV/dist/js/bootstrap.js"),
        Script from url(s"https://cdn.jsdelivr.net/webjars/org.webjars/bootstrap-datepicker/$BootstrapDatePickerV/js/bootstrap-datepicker.min.js")
      )
    }

    def styles: Seq[PageContent] = {
      Seq(
        // Bootstrap
        // sys.props.get("bootstrap-theme").fold(Style from url(s"https://raw.githubusercontent.com/twbs/bootstrap/v$BootstrapV/dist/css/bootstrap.css"))(themeCss),
        Style from url(s"https://cdn.jsdelivr.net/webjars/org.webjars/bootstrap-datepicker/$BootstrapDatePickerV/css/bootstrap-datepicker3.min.css"),
        // Font Awesome
        Style from url(s"https://raw.githubusercontent.com/FortAwesome/Font-Awesome/v$FontAwesomeV/css/font-awesome.css")
      )
    }

    def fonts: Seq[PageContent] = {
      fontPackage(
        "glyphicons-halflings-regular",
        s"https://raw.githubusercontent.com/twbs/bootstrap/v$BootstrapV/dist/fonts/glyphicons-halflings-regular"
      ) ++
        fontPackage("fontawesome-webfont", s"https://raw.githubusercontent.com/FortAwesome/Font-Awesome/v$FontAwesomeV/fonts/fontawesome-webfont")
    }

    def all: Seq[PageContent] = {
      scripts ++ styles ++ fonts
    }
  }

  def bootstrap: Seq[PageContent] = {
    Bootstrap.all
  }

  def videoJS: Seq[PageContent] = {
    val videoJs = github("videojs", "video.js", "v5.8.0") / "dist"
    Seq(
      Script from url(videoJs % "video.min.js"),
      Style from url(videoJs  % "video-js.min.css")
    )
  }

  def markedJS: Seq[PageContent] = {

    val highlightJSLanguages = Seq(
      "bash",
      "clojure",
      "coffeescript",
      "cpp",
      "cs",
      "d",
      "delphi",
      "erlang",
      "fsharp",
      "go",
      "groovy",
      "haskell",
      "java",
      "javascript",
      "json",
      "lua",
      "lisp",
      "markdown",
      "objectivec",
      "perl",
      "php",
      "python",
      "ruby",
      "rust",
      "scala",
      "scheme",
      "sql",
      "swift",
      "typescript",
      "css",
      "xml"
    )

    val highlightJSStyle = "github"

    val markedJS      = "org.webjars.bower" % "marked" % "0.3.5"
    val highlightJS   = "org.webjars" % "highlightjs" % "9.2.0"
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

    val highlightJSModules =
      for (lang ← highlightJSLanguages)
        yield Script from highlightJS / s"languages/$lang.min.js"

    scripts ++ highlightJSModules
  }

  def dropzoneJS = Seq[PageContent](
    Script from "org.webjars" % "dropzone" % "5.5.0" / "min/dropzone.min.js",
    Style from "org.webjars"  % "dropzone" % "5.5.0" / "min/dropzone.min.css"
  )

  def toastrJS = Seq[PageContent](
    Script from "org.webjars" % "toastr" % "2.1.2" / "build/toastr.min.js",
    Style from "org.webjars"  % "toastr" % "2.1.2" / "build/toastr.min.css"
  )

  def indexHtml: Seq[PageContent] = {
    Seq(Html from Assets.index, Style from Assets.style)
  }
}
