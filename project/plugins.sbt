addSbtPlugin("edu.gemini" % "sbt-lucuma-lib" % "0.12.1")

libraryDependencies ++= Seq(
  "org.scalablytyped.converter" %% "cli" % "1.0.0-beta44"
)

dependencyOverrides += "org.typelevel"          %% "cats-effect" % "3.5.6"
dependencyOverrides += "org.scala-lang.modules" %% "scala-xml"   % "2.3.0"
