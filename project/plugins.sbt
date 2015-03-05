addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.0-M4")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.13.0")

// Scoverage and Coveralls

resolvers += Resolver.sbtPluginRepo("snapshots")

addSbtPlugin("org.scoverage" %% "sbt-scoverage" % "1.0.4")

addSbtPlugin("org.scoverage" %% "sbt-coveralls" % "1.0.0.BETA1")
