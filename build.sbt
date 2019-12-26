name := "gfc-cache"

organization := "com.gilt"

scalaVersion := "2.12.10"

crossScalaVersions := Seq(scalaVersion.value)

scalacOptions += "-target:jvm-1.8"

javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

libraryDependencies ++= Seq(
  "com.gilt" %% "gfc-time" % "0.0.7",
  "com.gilt" %% "gfc-concurrent" % "0.3.8",
  "com.gilt" %% "gfc-logging" % "0.0.8",
  "com.gilt" %% "gfc-util" % "0.2.2",
  "com.gilt" %% "gfc-guava" % "0.3.1",
  "org.scalatest" %% "scalatest" % "3.1.0" % Test,
  "org.mockito" % "mockito-core" % "3.2.4" % Test
)


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
