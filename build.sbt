lazy val akkaHttpVersion = "10.1.4"
lazy val akkaVersion    = "2.5.15"
lazy val scalaTestVersion = "3.0.5"
lazy val scalaLoggingVersion = "3.9.0"
lazy val logbackVersion = "1.2.3"
lazy val mockitoVersion = "2.21.0"

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    inThisBuild(List(
      organization    := "com.github.alextokarew",
      scalaVersion    := "2.12.6"
    )),
    name := "Simple Money Transfer",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream"          % akkaVersion,

      "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
      "ch.qos.logback" % "logback-classic" % logbackVersion,

      "com.typesafe.akka" %% "akka-http-testkit"    % akkaHttpVersion  % Test,
      "com.typesafe.akka" %% "akka-testkit"         % akkaVersion      % Test,
      "com.typesafe.akka" %% "akka-stream-testkit"  % akkaVersion      % Test,
      "org.scalatest"     %% "scalatest"            % scalaTestVersion % "it,test",
      "org.mockito" % "mockito-core" % mockitoVersion % Test
    )
  )
