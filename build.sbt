import Dependencies._
import sbtcrossproject.CrossProject
// shadow sbt-scalajs' crossProject and CrossType until Scala.js 1.0.0 is released
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

scalaVersion in ThisBuild := "2.12.7"
lazy val settings = commonSettings

lazy val compilerOptions = Seq(
  "-unchecked",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-language:reflectiveCalls",
//  "-language:experimental.macros",
//  "-Ypartial-unification",
//  "-Yliteral-types",
//  "-Xlog-implicits",
  "-deprecation",
  "-encoding",
  "utf8"
)

lazy val projectSettings = Seq(
  organization := "eu.l-space",
  version := "0.2.0-SNAPSHOT",
)

lazy val commonSettings = projectSettings ++ Seq(
  scalacOptions ++= compilerOptions,
  scalaVersion := "2.12.7",
  crossScalaVersions := Seq("2.11.12", "2.12.7"),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    "jitpack" at "https://jitpack.io"
  ),
  //  parallelExecution in Test := true,
  publishArtifact in (Test, packageBin) := true,
  //  publishConfiguration := publishConfiguration.value.withOverwrite(true),
  //  publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true),
  updateOptions := updateOptions.value.withCachedResolution(true)
//    scalariformPreferences := scalariformPreferences.value
//        .setPreference(RewriteArrowSymbols, true)
)

lazy val lspace = project
  .in(file("."))
  .settings(skip in publish := true)
  .aggregate(core.jvm, core.js, parse.jvm, parse.js, client.jvm, client.js, graph, services)

lazy val core: CrossProject = (crossProject(JSPlatform, JVMPlatform).crossType(CrossType.Full) in file("core"))
  .settings(settings)
  .settings(
    name := "lspace-core",
    libraryDependencies ++= coreDeps.value
  )
  .jvmSettings(
    libraryDependencies ++= coreJvmDeps
  )
  .jsSettings(
    jsEnv in Test := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv(),
    libraryDependencies ++= coreJsDeps.value
  )

lazy val parse = (crossProject(JSPlatform, JVMPlatform).crossType(CrossType.Full) in file("parse"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(settings)
  .settings(
    name := "lspace-parse",
    libraryDependencies ++= parseDeps.value
  )
  .jvmSettings(
    libraryDependencies ++= parseJvmDeps
  )
  .jsSettings(
    jsEnv in Test := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv(),
    libraryDependencies ++= parseJsDeps.value
  )

lazy val client =
  (crossProject(JSPlatform, JVMPlatform).crossType(CrossType.Full) in file("client"))
    .dependsOn(parse % "compile->compile;test->test")
    .settings(settings)
    .settings(
      name := "lspace-client",
      libraryDependencies ++= clientDeps.value
    )
    .jvmSettings(
      libraryDependencies ++= clientJvmDeps
    )
    .jsSettings(
      jsEnv in Test := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv(),
      libraryDependencies ++= clientJsDeps.value
    )

lazy val graph = (project in file("graph"))
  .dependsOn(parse.jvm % "compile->compile;test->test")
  .settings(settings)
  .settings(
    name := "lspace-graph",
    libraryDependencies ++= graphDeps
  )

lazy val services = (project in file("services"))
  .dependsOn(graph)
  //  .dependsOn(parse.jvm)
  .dependsOn(client.jvm % "compile->compile;test->test")
  .settings(settings)
  .settings(
    name := "lspace-services",
    libraryDependencies ++= servicesDeps,
  )

val notPublish = Seq(
  publishArtifact := false,
  publish := {},
  publishLocal := {}
)

val makeSettingsYml = Def.task {
  val file     = (resourceManaged in Compile).value / "site" / "data" / "settings.yml"
  val contents = s"version: ${version.value}"
  IO.write(file, contents)
  Seq(file)
}

lazy val site = (project in file("site"))
  .enablePlugins(MicrositesPlugin)
  .dependsOn(services % "compile->compile;compile->test")
  .settings(name := "lspace-site")
  .settings(notPublish, test in Test := {})
  .settings(projectSettings)
  .settings(
    resourceGenerators in Compile += makeSettingsYml.taskValue,
    makeMicrosite := (makeMicrosite dependsOn makeSettingsYml).value
  )
  .settings(
    micrositeName := "L-space",
    micrositeDescription := "L-space, a graph computing framework for Scala",
    micrositeDataDirectory := (resourceManaged in Compile).value / "site" / "data",
    //    unmanagedResources ++= Seq(
    //
    //    ),
    //    micrositeDocumentationUrl := "/yoursite/docs",
    //    micrositeDocumentationLabelDescription := "Documentation",
    micrositeAuthor := "Thijs Broersen",
    micrositeHomepage := "https://docs.l-space.eu",
    micrositeOrganizationHomepage := "https://l-space.eu",
    //    micrositeOrganizationHomepage := "",
    micrositeGithubOwner := "L-space",
    micrositeGithubRepo := "L-space",
    micrositeGitterChannelUrl := "L-space/L-space",
    micrositeFooterText := Some(
      "<b>Knowledge is Power</b> <- <i>BOOKS = KNOWLEDGE = POWER = (FORCE X DISTANCE ÷ TIME)</i>")
  )
