lazy val scalaVersions = Seq("3.3.8", "2.13.18")

ThisBuild / scalaVersion := scalaVersions.head
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / organization := "de.lhns"
ThisBuild / version := (doobieFlyway.projectRefs.head / version).value
name := (doobieFlyway.projectRefs.head / name).value

val V = new {
  val betterMonadicFor = "0.3.1"
  val doobie = "1.0.0-RC13"
  val flyway = "13.2.0"
  val h2 = "2.4.240"
  val logbackClassic = "1.6.3"
  val munit = "1.2.4"
  val munitCatsEffect = "2.2.0"
}

lazy val commonSettings: SettingsDefinition = Def.settings(
  version := {
    val Tag = "refs/tags/v?([0-9]+(?:\\.[0-9]+)+(?:[+-].*)?)".r
    sys.env.get("CI_VERSION").collect { case Tag(tag) => tag }
      .getOrElse("0.0.1-SNAPSHOT")
  },

  licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0")),

  homepage := scmInfo.value.map(_.browseUrl),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/lhns/doobie-flyway"),
      "scm:git@github.com:lhns/doobie-flyway.git"
    )
  ),
  developers := List(
    Developer(id = "lhns", name = "Pierre Kisters", email = "pierrekisters@gmail.com", url = url("https://github.com/lhns/"))
  ),

  testFrameworks += new TestFramework("munit.Framework"),

  Compile / doc / sources := Seq.empty,

  publishMavenStyle := true,

  publishTo := sonatypePublishToBundle.value,

  sonatypeCredentialHost := Sonatype.sonatypeCentralHost,

  credentials ++= (for {
    username <- sys.env.get("SONATYPE_USERNAME")
    password <- sys.env.get("SONATYPE_PASSWORD")
  } yield Credentials(
    "Sonatype Nexus Repository Manager",
    sonatypeCredentialHost.value,
    username,
    password
  )).toList
)

// Settings for modules whose main sources are Scala. Not applied to flywayBaseline, which
// is Java-only in Compile and only pulls Scala in for its tests.
lazy val scalaSettings: SettingsDefinition = Def.settings(
  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % V.logbackClassic % Test,
    "org.typelevel" %% "munit-cats-effect" % V.munitCatsEffect % Test,
    "org.scalameta" %% "munit" % V.munit % Test,
  ),

  libraryDependencies ++= virtualAxes.?.value.getOrElse(Seq.empty).collectFirst {
    case VirtualAxis.ScalaVersionAxis(version, _) if version.startsWith("2.") =>
      compilerPlugin("com.olegpy" %% "better-monadic-for" % V.betterMonadicFor)
  },
)

lazy val root: Project =
  project
    .in(file("."))
    .settings(commonSettings)
    .settings(
      publishArtifact := false,
      publish / skip := true
    )
    .aggregate(flywayBaseline)
    .aggregate(doobieFlyway.projectRefs: _*)

// Java-only artifact: the Flyway baseline-migration support carries no doobie, cats-effect
// or Scala dependency, so it is usable from plain Java. Tests are Scala (munit) but never
// leak into the published Compile classpath.
lazy val flywayBaseline = project.in(file("flyway-baseline"))
  .settings(commonSettings)
  .settings(
    name := "flyway-baseline",

    crossPaths := false,
    autoScalaLibrary := false,

    javacOptions ++= Seq("--release", "17"),

    libraryDependencies ++= Seq(
      "org.flywaydb" % "flyway-core" % V.flyway,
      "org.scala-lang" %% "scala3-library" % scalaVersion.value % Test,
      "org.scalameta" %% "munit" % V.munit % Test,
      "com.h2database" % "h2" % V.h2 % Test,
      "ch.qos.logback" % "logback-classic" % V.logbackClassic % Test,
    ),
  )

lazy val doobieFlyway = projectMatrix.in(file("doobie-flyway"))
  .settings(commonSettings)
  .settings(scalaSettings)
  .settings(
    name := "doobie-flyway",

    libraryDependencies ++= Seq(
      "org.flywaydb" % "flyway-core" % V.flyway,
      "org.typelevel" %% "doobie-core" % V.doobie,
      "org.typelevel" %% "doobie-h2" % V.doobie % Test,
    ),
  )
  .dependsOn(flywayBaseline)
  .jvmPlatform(scalaVersions)
