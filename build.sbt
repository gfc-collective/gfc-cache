name := "gfc-cache"

organization := "com.gilt"

scalaVersion := "2.11.11"

crossScalaVersions := Seq(scalaVersion.value, "2.12.2", "2.10.6")

scalacOptions += "-target:jvm-1.7"

javacOptions ++= Seq("-source", "1.7", "-target", "1.7")

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.3" % "test",
  "com.gilt" %% "gfc-time" % "0.0.5",
  "com.gilt" %% "gfc-concurrent" % "0.3.3",
  "com.gilt" %% "gfc-concurrent" % "0.3.3",
  "com.gilt" %% "gfc-logging" % "0.0.7",
  "com.gilt" %% "gfc-util" % "0.1.7",
  "com.gilt" %% "gfc-guava" % "0.2.5"

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

licenses := Seq("Apache-style" -> url("https://raw.githubusercontent.com/gilt/gfc-cache/master/LICENSE"))

homepage := Some(url("https://github.com/gilt/gfc-cache"))

pomExtra := (
  <scm>
    <url>https://github.com/gilt/gfc-cache.git</url>
    <connection>scm:git:git@github.com:gilt/gfc-cache.git</connection>
  </scm>
)

