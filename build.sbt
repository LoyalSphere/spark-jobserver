import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

import Dependencies._
import sbt.{File, _}

updateOptions := updateOptions.value.withCachedResolution(true)
transitiveClassifiers in Global := Seq(Artifact.SourceClassifier)
lazy val dirSettings = Seq()

lazy val akkaApp = Project(id = "akka-app", base = file("akka-app"))
  .settings(description := "Common Akka application stack: metrics, tracing, logging, and more.")
  .settings(commonSettings)
  .settings(libraryDependencies ++= coreTestDeps ++ akkaDeps)
  .settings(publishSettings)
  .disablePlugins(SbtScalariform)

lazy val jobServer = Project(id = "job-server", base = file("job-server"))
  .settings(commonSettings)
  .settings(revolverSettings)
  .settings(multiJvmSettings: _*)
  .settings(assembly := null.asInstanceOf[File])
  .settings(
    description := "Spark as a Service: a RESTful job server for Apache Spark",
    libraryDependencies ++= sparkDeps ++ slickDeps ++
    securityDeps ++ coreTestDeps ++ zookeeperDeps ++ miscTestDeps ++ keycloakDeps,
    test in Test := (test in Test).dependsOn(packageBin in Compile in jobServerTestJar)
      .dependsOn(clean in Compile in jobServerTestJar)
      .dependsOn(buildPython in jobServerPython)
      .dependsOn(clean in Compile in jobServerPython)
      .value,
    testOnly in Test := (testOnly in Test).dependsOn(packageBin in Compile in jobServerTestJar)
      .dependsOn(clean in Compile in jobServerTestJar)
      .dependsOn(buildPython in jobServerPython)
      .dependsOn(clean in Compile in jobServerPython)
      .evaluated,
    console in Compile := Defaults.consoleTask(fullClasspath in Compile, console in Compile).value,
    fullClasspath in Compile := (fullClasspath in Compile).map { classpath =>
      extraJarPaths ++ classpath
    }.value,
    fork in Test := true
  )
  .settings(publishSettings)
  .dependsOn(akkaApp, jobServerApi)
  .disablePlugins(SbtScalariform)
  .configs(MultiJvm)

lazy val jobServerTestJar = Project(id = "job-server-tests", base = file("job-server-tests"))
  .settings(commonSettings)
  .settings(jobServerTestJarSettings)
  .settings(noPublishSettings)
  .dependsOn(jobServerApi)
  .disablePlugins(SbtScalariform)
  .disablePlugins(ScoverageSbtPlugin) // do not include in coverage report

lazy val jobServerApi = Project(id = "job-server-api", base = file("job-server-api"))
  .settings(commonSettings)
  .settings(jobServerApiSettings)
  .settings(publishSettings)
  .disablePlugins(SbtScalariform)

lazy val jobServerExtras = Project(id = "job-server-extras", base = file("job-server-extras"))
  .settings(commonSettings)
  .settings(jobServerExtrasSettings)
  .settings(
    test in Test := (test in Test)
      .dependsOn(packageBin in Compile in jobServerTestJar)
      .dependsOn(clean in Compile in jobServerTestJar)
      .dependsOn(buildPython in jobServerPython)
      .dependsOn(buildPyExamples in jobServerPython)
      .dependsOn(clean in Compile in jobServerPython)
      .value,
    testOnly in Test := (testOnly in Test)
      .dependsOn(packageBin in Compile in jobServerTestJar)
      .dependsOn(clean in Compile in jobServerTestJar)
      .dependsOn(buildPython in jobServerPython)
      .dependsOn(buildPyExamples in jobServerPython)
      .dependsOn(clean in Compile in jobServerPython)
      .evaluated
  )
  .dependsOn(jobServerApi, jobServer % "compile->compile; test->test")
  .disablePlugins(SbtScalariform)

lazy val jobServerPython = Project(id = "job-server-python", base = file("job-server-python"))
  .settings(commonSettings)
  .settings(jobServerPythonSettings)
  .dependsOn(jobServerApi, akkaApp % "test")
  .disablePlugins(SbtScalariform)

lazy val jobserverIntegrationTests = Project(id = "job-server-integration-tests", base = file("job-server-integration-tests"))
  .settings(commonSettings)
  .settings(jobserverIntegrationTestsSettings)

lazy val root = Project(id = "root", base = file("."))
  .settings(commonSettings)
  .settings(Release.settings)
  .settings(noPublishSettings)
  .settings(rootSettings)
  .settings(dockerSettings)
  .aggregate(jobServer, jobServerApi, jobServerTestJar, akkaApp, jobServerExtras, jobServerPython)
  .dependsOn(jobServer, jobServerExtras)
  .disablePlugins(SbtScalariform).enablePlugins(DockerPlugin)

lazy val jobServerExtrasSettings = revolverSettings ++ Assembly.settings ++ publishSettings ++ Seq(
  libraryDependencies ++= sparkExtraDeps,
  // Extras packages up its own jar for testing itself
  test in Test := (test in Test).dependsOn(packageBin in Compile).value,
  fork in Test := true,
  parallelExecution in Test := false,
  // Temporarily disable test for assembly builds so folks can package and get started.  Some tests
  // are flaky in extras esp involving paths.
  test in assembly := {},
  exportJars := true
)

lazy val jobServerApiSettings = Seq(libraryDependencies ++= sparkDeps ++ sparkExtraDeps)

lazy val testPython = taskKey[Unit]("Launch a sub process to run the Python tests")
lazy val buildPython = taskKey[Unit]("Build the python side of python support into a wheel and egg")
lazy val buildPyExamples = taskKey[Unit]("Build the examples of python jobs into a wheel and egg")

lazy val jobServerPythonSettings = revolverSettings ++ Assembly.settings ++ publishSettings ++ Seq(
  libraryDependencies ++= sparkPythonDeps,
  fork in Test := true,
  cancelable in Test := true,
  testPython := PythonTasks.testPythonTask(baseDirectory.value),
  buildPython := PythonTasks.buildPythonTask(baseDirectory.value, version.value),
  buildPyExamples := PythonTasks.buildExamplesTask(baseDirectory.value, version.value),
  assembly := assembly.dependsOn(buildPython).value
)

lazy val jobserverIntegrationTestsSettings = Seq(
  libraryDependencies ++= integrationTestDeps,
  mainClass in Compile := Some("spark.jobserver.integrationtests.IntegrationTests"),
)

lazy val jobServerTestJarSettings = Seq(
  libraryDependencies ++= sparkDeps ++ apiDeps,
  description := "Test jar for Spark Job Server",
  exportJars := true // use the jar instead of target/classes
)

lazy val noPublishSettings = Seq(
  publishTo := Some(Resolver.file("Unused repo", file("target/unusedrepo"))),
  publishArtifact := false,
  publish := {},
  skip in publish := true
)

lazy val dockerSettings = Seq(

  // Make the docker task depend on the assembly task, which generates a fat JAR file
  docker := docker.dependsOn(assembly in jobServerExtras).value,
  dockerfile in docker := {
    val artifact = (assemblyOutputPath in assembly in jobServerExtras).value
    val artifactTargetPath = s"/app/${artifact.name}"
    new sbtdocker.mutable.Dockerfile {
      from(s"openjdk:${Versions.java}")
      // Dockerfile best practices: https://docs.docker.com/articles/dockerfile_best-practices/
      expose(8090)
      expose(9999) // for JMX
      env("JOBSERVER_MEMORY", "1G")
      env("HADOOP_VERSION", Versions.hadoop.slice(0,1))
      env("SPARK_VERSION", Versions.spark)
      env("SPARK_HOME", "/var/spark")
      env("SCALA_VERSION", scalaBinaryVersion.value)
      run("mkdir", "-p", "${SPARK_HOME}")

      runRaw(
        """wget --no-verbose http://archive.apache.org/dist/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz && \
        tar -xvzf spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz && \
        mv spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION} ${SPARK_HOME} && \
        rm spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz
        """)

      //copy
      copy(new File(sys.env.getOrElse("SJS_LOCATION", "/var/sjs")) / "job-server-extras" / "config" / "spark-defaults.conf",  "${SPARK_HOME}/conf/")
      copy(new File(sys.env.getOrElse("SJS_LOCATION", "/var/sjs")) / "job-server-extras" / "config" / "hbase-site.xml",  "${SPARK_HOME}/conf/")

      copy(artifact, artifactTargetPath)
      copy(baseDirectory(_ / "bin" / "server_start.sh").value, file("app/server_start.sh"))
      copy(baseDirectory(_ / "bin" / "server_stop.sh").value, file("app/server_stop.sh"))
      copy(baseDirectory(_ / "bin" / "setenv.sh").value, file("app/setenv.sh"))
      copy(baseDirectory(_ / "config" / "log4j-stdout.properties").value, file("app/log4j-server.properties"))
      copy(baseDirectory(_ / "config" / "docker.conf").value, file("app/docker.conf"))
      copy(baseDirectory(_ / "config" / "docker.sh").value, file("app/settings.sh"))

      // Use a volume to persist database between container invocations
      //run("mkdir", "-p", "/database")
      //volume("/database")
      entryPoint("app/server_start.sh")
    }
  },
  imageNames in docker := Seq(
    sbtdocker.ImageName(namespace = Some("campaignmonitor"),
      repository = "edp-spark-jobserver",
      tag = Some(
        s"latest")
    )
  )
)

lazy val rootSettings = Seq(
  // Must run Spark tests sequentially because they compete for port 4040!
  parallelExecution in Test := false,
  publishArtifact := false,
  concurrentRestrictions := Seq(
    Tags.limit(Tags.CPU, java.lang.Runtime.getRuntime.availableProcessors()),
    // limit to 1 concurrent test task, even across sub-projects
    // Note: some components of tests seem to have the "Untagged" tag rather than "Test" tag.
    // So, we limit the sum of "Test", "Untagged" tags to 1 concurrent
    Tags.limitSum(1, Tags.Test, Tags.Untagged))
)

lazy val revolverSettings = Seq(
  javaOptions in reStart += jobServerLogging,
  // Give job server a bit more PermGen since it does classloading
  javaOptions in reStart += "-Djava.security.krb5.realm= -Djava.security.krb5.kdc=",
  // This lets us add Spark back to the classpath without assembly barfing
  fullClasspath in reStart := (fullClasspath in Compile).value,
  mainClass in reStart := Some("spark.jobserver.JobServer")
)

// To add an extra jar to the classpath when doing "re-start" for quick development, set the
// env var EXTRA_JAR to the absolute full path to the jar
lazy val extraJarPaths = Option(System.getenv("EXTRA_JAR"))
  .map(jarpath => Seq(Attributed.blank(file(jarpath))))
  .getOrElse(Nil)

// Create a default Scala style task to run with compiles
lazy val runScalaStyle = taskKey[Unit]("testScalaStyle")

lazy val commonSettings = Defaults.coreDefaultSettings ++ dirSettings ++ Seq(
  organization := "spark.jobserver",
  crossPaths   := true,
  crossScalaVersions := Seq("2.11.12", "2.12.12"),
  releaseIgnoreUntrackedFiles := true,
  scalaVersion := sys.env.getOrElse("SCALA_VERSION", "2.12.12"),
  dependencyOverrides += "org.scala-lang" % "scala-compiler" % scalaVersion.value,
  // scalastyleFailOnError := true,
  runScalaStyle := {
    scalastyle.in(Compile).toTask("").value
  },
  (compile in Compile) := (compile in Compile).dependsOn(runScalaStyle).value,

  // In Scala 2.10, certain language features are disabled by default, such as implicit conversions.
  // Need to pass in language options or import scala.language.* to enable them.
  // See SIP-18 (https://docs.google.com/document/d/1nlkvpoIRkx7at1qJEZafJwthZ3GeIklTFhqmXMvTX9Q/edit)
  scalacOptions := Seq(
    "-deprecation", "-feature",
    "-language:implicitConversions",
    "-language:postfixOps",
    "-language:existentials"
  ),
  // For Building on Encrypted File Systems...
  scalacOptions ++= Seq("-Xmax-classfile-name", "128"),
  resolvers ++= Dependencies.repos,
  libraryDependencies ++= apiDeps,
  parallelExecution in Test := false,
  testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oDF"),
  // We need to exclude jms/jmxtools/etc because it causes undecipherable SBT errors  :(
  ivyXML :=
    <dependencies>
      <exclude module="jms"/>
      <exclude module="jmxtools"/>
      <exclude module="jmxri"/>
    </dependencies>
) ++ scoverageSettings

lazy val scoverageSettings = {
  // Semicolon-separated list of regexs matching classes to exclude
  coverageExcludedPackages := ".+Benchmark.*;.+Example.*;.+TestJob"
}

lazy val publishSettings = Seq(
  licenses += ("Apache-2.0", url("http://choosealicense.com/licenses/apache/")),
  publishTo := Some("Artifactory Realm" at "https://sparkjobserver.jfrog.io/artifactory/jobserver"),
  credentials += Credentials("Artifactory Realm", "sparkjobserver.jfrog.io", System.getenv("JFROG_USERNAME"), System.getenv("JFROG_PASSWORD"))
)

// This is here so we can easily switch back to Logback when Spark fixes its log4j dependency.
lazy val jobServerLogbackLogging = "-Dlogback.configurationFile=config/logback-local.xml"
lazy val jobServerLogging = "-Dlog4j.configuration=file:config/log4j-local.properties"
