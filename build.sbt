lazy val commonSettings = Seq(
  organization := "com.gilt",
  name := "gfc-cache",
  scalaVersion := "2.11.11",
  crossScalaVersions := Seq(scalaVersion.value, "2.10.6", "2.12.2"),
  scalacOptions += "-target:jvm-1.7",
  javacOptions ++= Seq("-source", "1.7", "-target", "1.7")
)

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .settings(releaseSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.gilt" %% "gfc-time" % "0.0.7",
      "com.gilt" %% "gfc-concurrent" % "0.3.5",
      "com.gilt" %% "gfc-logging" % "0.0.7",
      "com.gilt" %% "gfc-util" % "0.1.7",
      "com.gilt" %% "gfc-guava" % "0.2.5",
      "org.scalatest" %% "scalatest" % "3.0.3" % "test",
      "org.mockito" % "mockito-all" % "1.9.5" % "test"
    )
  )

lazy val releaseSettings = Seq(
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  licenses := Seq("Apache-style" -> url("https://raw.githubusercontent.com/gilt/gfc-cache/master/LICENSE")),
  homepage := Some(url("https://github.com/gilt/gfc-cache")),
  pomExtra := <scm>
    <url>https://github.com/gilt/gfc-cache.git</url>
    <connection>scm:git:git@github.com:gilt/gfc-cache.git</connection>
  </scm>
    <developers>
      <developer>
        <id>gheine</id>
        <name>Gregor Heine</name>
        <url>https://github.com/gheine</url>
      </developer>
    </developers>
)
