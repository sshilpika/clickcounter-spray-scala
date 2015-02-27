name := "clickcounter-spray-scala"

version := "0.1"

scalaVersion := "2.11.4"

resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"

libraryDependencies ++= {
  val akkaV = "2.3.6"
  val sprayV = "1.3.2"
  val sprayJsonV = "1.3.1"
  Seq(
    "io.spray"            %%  "spray-can"     % sprayV,
    "io.spray"            %%  "spray-routing" % sprayV,
    "io.spray"            %%  "spray-json"    % sprayJsonV,
    "io.spray"            %%  "spray-testkit" % sprayV      % Test,
    "io.spray"            %%  "spray-testkit" % sprayV      % Test,
    "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
    "org.specs2"          %%  "specs2-core"   % "2.3.11"    % Test,
    "org.slf4j"           %   "slf4j-simple"  % "1.7.10",
    "com.livestream"      %%  "scredis"       % "2.0.6",
    "net.databinder.dispatch" %% "dispatch-core" % "0.11.2" % Test
  )
}

// IntelliJ Scala plugin reports false positive errors here

Revolver.settings

enablePlugins(JavaAppPackaging)
