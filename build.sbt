val scala3 = "3.2.2"

Global / onLoad                := {
  val old = (Global / onLoad).value
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

  old
}

ThisBuild / tlBaseVersion      := "0.0"
ThisBuild / crossScalaVersions := Seq(scala3)

ThisBuild / tlCiReleaseBranches                := Seq("main")
ThisBuild / githubWorkflowBuildSbtStepPreamble := Seq()
ThisBuild / githubWorkflowArtifactUpload       := true
ThisBuild / githubWorkflowTargetBranches += "!dependabot/**"
ThisBuild / githubWorkflowBuildPreamble ++= Seq(
  WorkflowStep.Use(
    UseRef.Public("actions", "setup-node", "v3"),
    params = Map("node-version" -> "18", "cache" -> "npm")
  ),
  WorkflowStep.Run(
    List("npm install")
  )
)

ThisBuild / mergifyPrRules +=
  MergifyPrRule(
    "merge dependabot PRs",
    MergifyCondition.Custom("author=dependabot[bot]") :: mergifySuccessConditions.value.toList,
    List(MergifyAction.Merge())
  )

lazy val stOut = Def.setting { (npm: String) =>
  val fn                 = npm.replace("@", "").replace("/", "__")
  val finder: PathFinder =
    (ThisBuild / baseDirectory).value /
      "out" / fn(0).toString / fn / "src" / "main" ** "*.scala"
  finder.get
}

ThisBuild / tlFatalWarnings := false
ThisBuild / scalacOptions += "-language:implicitConversions"

lazy val root = project
  .in(file("."))
  .enablePlugins(NoPublishPlugin)
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
    reactPopper,
    reactDatepicker,
    tanstackTableCore,
    tanstackReactTable,
    tanstackReactVirtual
  )

lazy val std = project
  .settings(
    name := "lucuma-typed-std",
    Compile / managedSources ++= stOut.value("std"),
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "core"                  % "2.1.1",
      "com.olvind"                        %%% "scalablytyped-runtime" % "2.4.2"
    )
  )
  .enablePlugins(ScalaJSPlugin)
  .disablePlugins(MergifyPlugin)

lazy val csstype = project
  .settings(
    name := "lucuma-typed-csstype",
    Compile / managedSources ++= stOut.value("csstype")
  )
  .dependsOn(std)
  .enablePlugins(ScalaJSPlugin)
  .disablePlugins(MergifyPlugin)

lazy val dateFns = project
  .settings(
    name := "lucuma-typed-date-fns",
    Compile / managedSources ++= stOut.value("date-fns")
  )
  .dependsOn(std)
  .enablePlugins(ScalaJSPlugin)
  .disablePlugins(MergifyPlugin)

lazy val highcharts = project
  .settings(
    name := "lucuma-typed-highcharts",
    Compile / managedSources ++= stOut.value("highcharts")
  )
  .dependsOn(std)
  .enablePlugins(ScalaJSPlugin)
  .disablePlugins(MergifyPlugin)

lazy val propTypes = project
  .settings(
    name := "lucuma-typed-prop-types",
    Compile / managedSources ++= stOut.value("prop-types")
  )
  .dependsOn(std)
  .enablePlugins(ScalaJSPlugin)
  .disablePlugins(MergifyPlugin)

lazy val scheduler = project
  .settings(
    name := "lucuma-typed-scheduler",
    Compile / managedSources ++= stOut.value("scheduler")
  )
  .dependsOn(std)
  .enablePlugins(ScalaJSPlugin)
  .disablePlugins(MergifyPlugin)

lazy val react = project
  .settings(
    name := "lucuma-typed-react",
    Compile / managedSources ++= stOut.value("react")
  )
  .dependsOn(csstype, propTypes, scheduler)
  .enablePlugins(ScalaJSPlugin)
  .disablePlugins(MergifyPlugin)

lazy val reactTransitionGroup = project
  .settings(
    name := "lucuma-typed-react-transition-group",
    Compile / managedSources ++= stOut.value("react-transition-group")
  )
  .dependsOn(react)
  .enablePlugins(ScalaJSPlugin)
  .disablePlugins(MergifyPlugin)

lazy val primereact = project
  .settings(
    name := "lucuma-typed-primereact",
    Compile / managedSources ++= stOut.value("primereact")
  )
  .dependsOn(reactTransitionGroup)
  .enablePlugins(ScalaJSPlugin)
  .disablePlugins(MergifyPlugin)

lazy val reactPopper = project
  .settings(
    name := "lucuma-typed-react-popper",
    Compile / managedSources ++= stOut.value("react-popper")
  )
  .dependsOn(react)
  .enablePlugins(ScalaJSPlugin)
  .disablePlugins(MergifyPlugin)

lazy val reactDatepicker = project
  .settings(
    name := "lucuma-typed-react-datepicker",
    Compile / managedSources ++= stOut.value("react-datepicker")
  )
  .dependsOn(reactPopper, dateFns)
  .enablePlugins(ScalaJSPlugin)
  .disablePlugins(MergifyPlugin)

lazy val tanstackTableCore = project
  .settings(
    name := "lucuma-typed-tanstack-table-core",
    Compile / managedSources ++= stOut.value("@tanstack/table-core")
  )
  .dependsOn(std)
  .enablePlugins(ScalaJSPlugin)
  .disablePlugins(MergifyPlugin)

lazy val tanstackReactTable = project
  .settings(
    name := "lucuma-typed-tanstack-react-table",
    Compile / managedSources ++= stOut.value("@tanstack/react-table")
  )
  .dependsOn(react, tanstackTableCore)
  .enablePlugins(ScalaJSPlugin)
  .disablePlugins(MergifyPlugin)

lazy val tanstackReactVirtual = project
  .settings(
    name := "lucuma-typed-tanstack-react-virtual",
    Compile / managedSources ++= stOut.value("@tanstack/react-virtual")
  )
  .dependsOn(react)
  .enablePlugins(ScalaJSPlugin)
  .disablePlugins(MergifyPlugin)
