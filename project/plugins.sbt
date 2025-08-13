addSbtPlugin("edu.gemini" % "sbt-lucuma-lib" % "0.12.13")

libraryDependencies ++= Seq(
  "org.scalablytyped.converter" %% "cli" % "1.0.0-beta44"
)

dependencyOverrides += "org.typelevel"          %% "cats-effect" % "2.5.5"
dependencyOverrides += "org.scala-lang.modules" %% "scala-xml"   % "2.4.0"

// This overrides the sbt-scalajs plugin version set in stb-lucuma. With
// versions higher than 1.17.0, scalablyTyped code generation fails.
dependencyOverrides += {
  val sbtV = (pluginCrossBuild / sbtBinaryVersion).value
  val scalaV = (update / scalaBinaryVersion).value
  val dependency = "org.scala-js" % "sbt-scalajs" % "1.17.0"
  sbt.Defaults.sbtPluginExtra(dependency, sbtV, scalaV)
}

