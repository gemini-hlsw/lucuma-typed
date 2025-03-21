val scala3 = "3.6.4"

ThisBuild / tlBaseVersion      := "0.8"
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

lazy val lucumaTypedGenerate = taskKey[Unit]("Generate the ST facades")
lucumaTypedGenerate := {
  STConvert.main(
    Array(
      "--outputPackage",
      "lucuma.typed",
      "-f",
      "scalajs-react",
      "--scala",
      scala3,
      "--scalajs",
      scalaJSVersion
    )
  )

  stOut.value("primereact").foreach { f =>
    val content     = IO.read(f)
    // use the ESM-style sources in imports
    val transformed = content.replaceAll(
      """@JSImport\("primereact\/((.+?)(?<!\.esm))",""",
      """@JSImport("primereact/$1.esm","""
    )
    if (transformed != content)
      IO.write(f, transformed)
  }
}

ThisBuild / tlFatalWarnings := false
ThisBuild / scalacOptions += "-language:implicitConversions"

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
    floatingUIUtils,
    floatingUICore,
    floatingUIDom,
    floatingUIReactDom,
    floatingUIReact,
    reactDatepicker,
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
      "com.github.japgolly.scalajs-react" %%% "core"                  % "3.0.0-beta10",
      "com.olvind"                        %%% "scalablytyped-runtime" % "2.4.2"
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

lazy val floatingUIUtils = project
  .settings(
    name := "lucuma-typed-floatingui-utils"
  )
  .settings(facadeSettings("@floating-ui/utils"))
  .dependsOn(std)
  .enablePlugins(ScalaJSPlugin)

lazy val floatingUICore = project
  .settings(
    name := "lucuma-typed-floatingui-core"
  )
  .settings(facadeSettings("@floating-ui/core"))
  .dependsOn(floatingUIUtils)
  .enablePlugins(ScalaJSPlugin)

lazy val floatingUIDom = project
  .settings(
    name := "lucuma-typed-floatingui-dom"
  )
  .settings(facadeSettings("@floating-ui/dom"))
  .dependsOn(floatingUICore)
  .enablePlugins(ScalaJSPlugin)

lazy val floatingUIReactDom = project
  .settings(
    name := "lucuma-typed-floatingui-react-dom"
  )
  .settings(facadeSettings("@floating-ui/react-dom"))
  .dependsOn(react, floatingUIDom)
  .enablePlugins(ScalaJSPlugin)

lazy val floatingUIReact = project
  .settings(
    name := "lucuma-typed-floatingui-react"
  )
  .settings(facadeSettings("@floating-ui/react"))
  .dependsOn(floatingUIReactDom)
  .enablePlugins(ScalaJSPlugin)

lazy val reactDatepicker = project
  .settings(
    name := "lucuma-typed-react-datepicker"
  )
  .settings(facadeSettings("react-datepicker"))
  .dependsOn(react, floatingUIReact, dateFns)
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
