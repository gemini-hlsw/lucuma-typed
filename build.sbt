import scala.sys.process.*

val scala3 = "3.7.4"

val ScalablyTypedCliVersion     = "1.0.0-beta44"
val ScalablyTypedRuntimeVersion = "2.4.2"
val ScalaJSReactVersion         = "3.0.0"

ThisBuild / tlBaseVersion      := "0.10"
ThisBuild / crossScalaVersions := Seq(scala3)

ThisBuild / tlCiReleaseBranches                := Seq("main")
ThisBuild / githubWorkflowBuildTimeoutMinutes  := Some(120)
ThisBuild / githubWorkflowBuildSbtStepPreamble := Seq()
ThisBuild / githubWorkflowArtifactUpload       := true
ThisBuild / githubWorkflowTargetBranches += "!dependabot/**"
ThisBuild / githubWorkflowBuildPreamble ++= Seq(
  WorkflowStep.Use(
    UseRef.Public("actions", "setup-node", "v3"),
    params = Map("node-version" -> "20", "cache" -> "npm")
  ),
  WorkflowStep.Run(
    List("npm ci")
  )
)

lazy val reportHeap = taskKey[Unit]("Report JVM heap usage (max/total/used/free in MB)")

reportHeap := {
  val rt                  = java.lang.Runtime.getRuntime
  def mb(n: Long): Double = n.toDouble / 1024.0 / 1024.0
  val max                 = mb(rt.maxMemory)
  val total               = mb(rt.totalMemory)
  val free                = mb(rt.freeMemory)
  val used                = total - free

  val fmt = f"JVM heap (MB): max=${max}%.1f total=${total}%.1f used=${used}%.1f free=${free}%.1f"
  streams.value.log.info(fmt)
}

ThisBuild / githubWorkflowBuildPreamble +=
  WorkflowStep.Use(
    UseRef.Public("VirtusLab", "scala-cli-setup", "v1.5"),
    name = Some("Setup scala-cli")
  )

ThisBuild / githubWorkflowBuild ~= { steps =>
  WorkflowStep.Sbt(List("lucumaTypedGenerate")) +: steps
}

ThisBuild / mergifyPrRules +=
  MergifyPrRule(
    "merge dependabot PRs",
    MergifyCondition.Custom("author=dependabot[bot]") :: mergifySuccessConditions.value.toList,
    List(MergifyAction.Merge())
  )

lazy val stBase = Def.setting { (npm: String) =>
  val fn = npm.replace("@", "").replace("/", "__")

  (LocalRootProject / target).value / "scalably-typed" / fn(0).toString / fn
}

lazy val stOut = Def.setting { (npm: String) =>
  val finder: PathFinder = stBase.value(npm) / "src" / "main" ** "*.scala"
  finder.get
}

def fixFileContent(f: File, fix: String => String): Unit = {
  val content     = IO.read(f)
  val transformed = fix(content)
  if (transformed != content)
    IO.write(f, transformed)
}

lazy val lucumaTypedGenerate = taskKey[Unit]("Generate the ST facades")
lucumaTypedGenerate := {
  // Prune unused files from highcharts
  "./prune-files.js node_modules/highcharts highcharts-kept-files.txt" !

  "./prune-types.js --types-file highcharts-removed-types.txt node_modules/highcharts/highcharts.src.d.ts" !

  val convertArgs =
    List(
      "--outputPackage",
      "lucuma.typed",
      "-f",
      "scalajs-react",
      "--scala",
      scala3,
      "--scalajs",
      scalaJSVersion
    ).mkString(" ")

  s"scala-cli --scala 2.12.18 --dependency org.scalablytyped.converter::cli:${ScalablyTypedCliVersion} STConvert/STConvert.scala -- $convertArgs" !

  // use the ESM-style sources in imports
  stOut.value("primereact").foreach { f =>
    fixFileContent(
      f,
      _.replaceAll(
        """@JSImport\("primereact\/((.+?)(?<!\.esm))",""",
        """@JSImport("primereact/$1.esm","""
      )
    )
  }
}

ThisBuild / tlFatalWarnings := false
ThisBuild / scalacOptions += "-language:implicitConversions"

// Suppress compiler warnings. This is all generated code and there are thousands of
// "unused" warnings, etc. You may want to comment this out to troubleshoot the ST conversion.
ThisBuild / scalacOptions += "-Wconf:any:silent"

ThisBuild / scalacOptions += "-Xno-enrich-error-messages"

lazy val root = project
  .in(file("."))
  .enablePlugins(NoPublishPlugin)
  .settings(
    githubWorkflowArtifactUpload := true // sources are generated here
  )
  .aggregate(
    std,
    csstype,
    dateFns,
    highcharts,
    propTypes,
    scheduler,
    react,
    reactTransitionGroup,
    primereact,
    tanstackTableCore,
    tanstackReactTable,
    tanstackVirtualCore,
    tanstackReactVirtual
  )

def facadeSettings(npm: String) = Seq(
  Compile / managedSources ++= stOut.value(npm),
  Compile / packageSrc / mappings := {
    val base = stBase.value(npm)
    (Compile / managedSources).value.map { file =>
      file -> file.relativeTo(base).get.getPath
    }
  }
)

lazy val std = project
  .settings(
    name := "lucuma-typed-std",
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "core"                  % ScalaJSReactVersion,
      "com.olvind"                        %%% "scalablytyped-runtime" % ScalablyTypedRuntimeVersion
    )
  )
  .settings(facadeSettings("std"))
  .enablePlugins(ScalaJSPlugin)

lazy val csstype = project
  .settings(
    name := "lucuma-typed-csstype"
  )
  .settings(facadeSettings("csstype"))
  .dependsOn(std)
  .enablePlugins(ScalaJSPlugin)

lazy val dateFns = project
  .settings(
    name := "lucuma-typed-date-fns"
  )
  .settings(facadeSettings("date-fns"))
  .dependsOn(std)
  .enablePlugins(ScalaJSPlugin)

lazy val highcharts = project
  .settings(
    name := "lucuma-typed-highcharts"
  )
  .settings(facadeSettings("highcharts"))
  .dependsOn(std)
  .enablePlugins(ScalaJSPlugin)

lazy val propTypes = project
  .settings(
    name := "lucuma-typed-prop-types"
  )
  .settings(facadeSettings("prop-types"))
  .dependsOn(std)
  .enablePlugins(ScalaJSPlugin)

lazy val scheduler = project
  .settings(
    name := "lucuma-typed-scheduler"
  )
  .settings(facadeSettings("scheduler"))
  .dependsOn(std)
  .enablePlugins(ScalaJSPlugin)

lazy val react = project
  .settings(
    name := "lucuma-typed-react"
  )
  .settings(facadeSettings("react"))
  .dependsOn(csstype, propTypes, scheduler)
  .enablePlugins(ScalaJSPlugin)

lazy val reactTransitionGroup = project
  .settings(
    name := "lucuma-typed-react-transition-group"
  )
  .settings(facadeSettings("react-transition-group"))
  .dependsOn(react)
  .enablePlugins(ScalaJSPlugin)

lazy val primereact = project
  .settings(
    name := "lucuma-typed-primereact"
  )
  .settings(facadeSettings("primereact"))
  .dependsOn(reactTransitionGroup)
  .enablePlugins(ScalaJSPlugin)

lazy val tanstackTableCore = project
  .settings(
    name := "lucuma-typed-tanstack-table-core"
  )
  .settings(facadeSettings("@tanstack/table-core"))
  .dependsOn(std)
  .enablePlugins(ScalaJSPlugin)

lazy val tanstackReactTable = project
  .settings(
    name := "lucuma-typed-tanstack-react-table"
  )
  .settings(facadeSettings("@tanstack/react-table"))
  .dependsOn(react, tanstackTableCore)
  .enablePlugins(ScalaJSPlugin)

lazy val tanstackVirtualCore = project
  .settings(
    name := "lucuma-typed-tanstack-virtual-core"
  )
  .settings(facadeSettings("@tanstack/virtual-core"))
  .dependsOn(std)
  .enablePlugins(ScalaJSPlugin)

lazy val tanstackReactVirtual = project
  .settings(
    name := "lucuma-typed-tanstack-react-virtual"
  )
  .settings(facadeSettings("@tanstack/react-virtual"))
  .dependsOn(react, tanstackVirtualCore)
  .enablePlugins(ScalaJSPlugin)
