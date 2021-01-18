import scoverage.ScoverageKeys

name := "gfc-cache"

organization := "org.gfccollective"

scalaVersion := "2.13.4"

crossScalaVersions := Seq(scalaVersion.value, "2.12.13")

scalacOptions += "-target:jvm-1.8"

javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

libraryDependencies ++= Seq(
  "org.gfccollective" %% "gfc-time" % "1.0.0",
  "org.gfccollective" %% "gfc-concurrent" % "1.0.0",
  "org.gfccollective" %% "gfc-logging" % "1.0.0",
  "org.gfccollective" %% "gfc-util" % "1.0.0",
  "org.gfccollective" %% "gfc-guava" % "1.0.0",
  "org.scalatest" %% "scalatest" % "3.2.3" % Test,
  "org.scalatestplus" %% "mockito-3-2" % "3.1.2.0" % Test,
  "org.mockito" % "mockito-core" % "3.7.7" % Test,
)

ScoverageKeys.coverageMinimum := 82.4

ScoverageKeys.coverageFailOnMinimum := true

releaseCrossBuild := true

releasePublishArtifactsAction := PgpKeys.publishSigned.value

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

licenses := Seq("Apache-style" -> url("https://raw.githubusercontent.com/gfc-collective/gfc-cache/master/LICENSE"))

homepage := Some(url("https://github.com/gfc-collective/gfc-cache"))

pomExtra := <scm>
  <url>https://github.com/gfc-collective/gfc-cache.git</url>
  <connection>scm:git:git@github.com:gfc-collective/gfc-cache.git</connection>
</scm>
  <developers>
    <developer>
      <id>gheine</id>
      <name>Gregor Heine</name>
      <url>https://github.com/gheine</url>
    </developer>
  </developers>
