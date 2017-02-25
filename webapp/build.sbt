name := "shadowcloud-webapp"

libraryDependencies ++= Seq(
  "com.github.karasiq" %%% "scalajs-bootstrap" % "1.1.4"
)

persistLauncher in Compile := true