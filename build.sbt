lazy val root = project
  .in(file("."))
  .aggregate(
    csstype,
    `date-fns`,
    highcharts,
    primereact,
    `prop-types`,
    react,
    `react-datepicker`,
    `react-popper`,
    `react-transition-group`,
    scheduler,
    std,
    `tanstack__react-table`,
    `tanstack__react-virtual`,
    `tanstack__table-core`
  )

ThisBuild / tlBaseVersion := "0.0"
ThisBuild / sonatypeProfileName := "edu.gemini"

lazy val csstype = project.in(file("out/c/csstype"))
lazy val `date-fns` = project.in(file("out/d/date-fns"))
lazy val highcharts = project.in(file("out/h/highcharts"))
lazy val primereact = project.in(file("out/p/primereact"))
lazy val `prop-types` = project.in(file("out/p/prop-types"))
lazy val react = project.in(file("out/r/react"))
lazy val `react-datepicker` = project.in(file("out/r/react-datepicker"))
lazy val `react-popper` = project.in(file("out/r/react-popper"))
lazy val `react-transition-group` =
  project.in(file("out/r/react-transition-group"))
lazy val scheduler = project.in(file("out/s/scheduler"))
lazy val std = project.in(file("out/s/std"))
lazy val `tanstack__react-table` =
  project.in(file("out/t/tanstack__react-table"))
lazy val `tanstack__react-virtual` =
  project.in(file("out/s/tanstack__react-virtual"))
lazy val `tanstack__table-core` = project.in(file("out/s/tanstack__table-core"))
