addSbtPlugin("edu.gemini" % "sbt-lucuma-lib" % "0.12.7")

libraryDependencies ++= Seq(
  "org.scalablytyped.converter" %% "cli" % "1.0.0-beta44"
)

dependencyOverrides += "org.typelevel"          %% "cats-effect" % "2.5.5"
dependencyOverrides += "org.scala-lang.modules" %% "scala-xml"   % "2.3.0"
