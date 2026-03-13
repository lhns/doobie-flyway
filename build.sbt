lazy val scalaVersions = Seq("3.3.7", "2.13.18")

ThisBuild / scalaVersion := scalaVersions.head
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / organization := "de.lhns"
ThisBuild / version := (core.projectRefs.head / version).value
name := (core.projectRefs.head / name).value

val V = new {
  val betterMonadicFor = "0.3.1"
  val doobie = "1.0.0-RC12"
  val flyway = "11.20.3"
  val h2 = "2.3.232"
  val junitJupiter = "5.10.3"
  val junitInterface = "0.13.3"
  val logbackClassic = "1.5.32"
  val munit = "1.2.2"
  val munitCatsEffect = "2.1.0"
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

lazy val scalaSettings: SettingsDefinition = Def.settings(
  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % V.logbackClassic % Test,
    "org.typelevel" %% "munit-cats-effect" % V.munitCatsEffect % Test,
    "org.scalameta" %% "munit" % V.munit % Test,
  ),

  testFrameworks += new TestFramework("munit.Framework"),

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
    .aggregate(flywayBaseline, core.projectRefs: _*)

lazy val flywayBaseline = project.in(file("flyway-baseline"))
  .settings(commonSettings)
  .settings(
    name := "flyway-baseline",
    crossPaths := false,
    autoScalaLibrary := false,

    libraryDependencies ++= Seq(
      "org.flywaydb" % "flyway-core" % V.flyway,
      "com.h2database" % "h2" % V.h2 % Test,
      "org.junit.jupiter" % "junit-jupiter" % V.junitJupiter % Test,
      "com.github.sbt" % "junit-interface" % V.junitInterface % Test,
    ),
  )

lazy val core = projectMatrix.in(file("core"))
  .settings(commonSettings)
  .settings(scalaSettings)
  .settings(
    name := "doobie-flyway",

    libraryDependencies ++= Seq(
      "org.flywaydb" % "flyway-core" % V.flyway,
      "org.tpolecat" %% "doobie-core" % V.doobie,
      "org.tpolecat" %% "doobie-h2" % V.doobie % Test,
    ),
  )
  .dependsOn(flywayBaseline)
  .jvmPlatform(scalaVersions)
