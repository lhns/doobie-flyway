lazy val scalaVersions = Seq("3.2.0", "2.13.9")

ThisBuild / scalaVersion := scalaVersions.head
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / organization := "de.lhns"

lazy val commonSettings: SettingsDefinition = Def.settings(
  version := {
    val Tag = "refs/tags/(.*)".r
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

  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.4.1" % Test,
    "de.lolhens" %% "munit-tagless-final" % "0.2.0" % Test,
    "org.scalameta" %% "munit" % "0.7.29" % Test,
  ),

  testFrameworks += new TestFramework("munit.Framework"),

  libraryDependencies ++= virtualAxes.?.value.getOrElse(Seq.empty).collectFirst {
    case VirtualAxis.ScalaVersionAxis(version, _) if version.startsWith("2.") =>
      compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
  },

  Compile / doc / sources := Seq.empty,

  publishMavenStyle := true,

  publishTo := sonatypePublishToBundle.value,

  sonatypeCredentialHost := {
    if (sonatypeProfileName.value == "de.lolhens")
      "oss.sonatype.org"
    else
      "s01.oss.sonatype.org"
  },

  credentials ++= (for {
    username <- sys.env.get("SONATYPE_USERNAME")
    password <- sys.env.get("SONATYPE_PASSWORD")
  } yield Credentials(
    "Sonatype Nexus Repository Manager",
    sonatypeCredentialHost.value,
    username,
    password
  )).toList,

  pomExtra := {
    if (sonatypeProfileName.value == "de.lolhens")
      <distributionManagement>
        <relocation>
          <groupId>de.lhns</groupId>
        </relocation>
      </distributionManagement>
    else
      pomExtra.value
  }
)

name := (core.projectRefs.head / name).value

val V = new {
  val doobie = "1.0.0-RC2"
  val flyway = "9.3.1"
}

lazy val root: Project =
  project
    .in(file("."))
    .settings(commonSettings)
    .settings(
      publishArtifact := false,
      publish / skip := true
    )
    .aggregate(core.projectRefs: _*)

lazy val core = projectMatrix.in(file("core"))
  .settings(commonSettings)
  .settings(
    name := "doobie-flyway",

    libraryDependencies ++= Seq(
      "org.flywaydb" % "flyway-core" % V.flyway,
      "org.tpolecat" %% "doobie-core" % V.doobie,
    ),
  )
  .jvmPlatform(scalaVersions)
