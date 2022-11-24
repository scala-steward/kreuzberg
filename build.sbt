ThisBuild / version := "0.1.1-SNAPSHOT"

ThisBuild / scalaVersion := "3.2.1"

ThisBuild / Compile / run / fork := true
ThisBuild / Test / run / fork    := true

ThisBuild / organization := "net.reactivecore"

val scalaTestDeps = Seq(
  "org.scalatest" %% "scalatest" % "3.2.14" % Test,
  "org.scalatest" %% "scalatest-flatspec" % "3.2.14" % Test
)

lazy val lib = (crossProject(JSPlatform, JVMPlatform) in file("lib"))
  .settings(
    name := "kreuzberg",
    libraryDependencies ++= Seq(
      "com.lihaoyi"   %% "scalatags"   % "0.11.1",
      "com.lihaoyi"  %%% "scalatags"   % "0.11.1"
    ) ++ scalaTestDeps
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.3.0"
    )
  )

lazy val extras = (crossProject(JSPlatform, JVMPlatform) in file("extras"))
  .settings(
    name := "kreuzberg-extras"
  )
  .dependsOn(lib)

lazy val examples = (crossProject(JSPlatform, JVMPlatform) in file("examples"))
  .settings(
    name            := "examples",
    publishArtifact := false,
    publish         := {},
    publishLocal    := {}
  )
  .jsSettings(
    // Moving JavaScript to a place, where we can easily find it by the server
    Compile / fastOptJS / artifactPath := baseDirectory.value / "target/client_bundle/client/fast/main.js",
    Compile / fullOptJS / artifactPath := baseDirectory.value / "target/client_bundle/client/opt/main.js",
    scalaJSUseMainModuleInitializer    := true
  )
  .dependsOn(lib, extras)

lazy val miniserver = (project in file("miniserver"))
  .settings(
    name := "kreuzberg-miniserver",
    libraryDependencies ++= Seq(
      "io.d11"                     %% "zhttp"           % "2.0.0-RC11",
      "ch.qos.logback"              % "logback-classic" % "1.2.11",
      "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.4"
    ) ++ scalaTestDeps
  )
  .dependsOn(lib.jvm)

lazy val root = (project in file("."))
  .settings(
    name            := "kreuzberg-root",
    publish         := {},
    publishLocal    := {},
    test            := {},
    publishArtifact := false
  )
  .aggregate(
    lib.js,
    lib.jvm,
    extras.js,
    extras.jvm,
    miniserver,
    examples.js,
    examples.jvm
  )
