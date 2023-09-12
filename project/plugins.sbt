addSbtPlugin("edu.gemini" % "sbt-lucuma-lib" % "0.11.7")

libraryDependencies ++= Seq(
  "org.scalablytyped.converter" %% "cli" % "1.0.0-beta42"
)

dependencyOverrides += "org.typelevel"          %% "cats-effect" % "2.1.3"
dependencyOverrides += "org.scala-lang.modules" %% "scala-xml"   % "2.1.0"
