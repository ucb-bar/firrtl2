// SPDX-License-Identifier: Apache-2.0

enablePlugins(SiteScaladocPlugin)

lazy val commonSettings = Seq(
  organization := "edu.berkeley.cs",
  scalaVersion := "2.13.12",
  crossScalaVersions := Seq("2.13.12", "3.3.1")
)

lazy val firrtlSettings = Seq(
  name := "firrtl2",
  version := "6.0-SNAPSHOT",
  scalacOptions := Seq(
    "-deprecation",
    "-unchecked",
    "-language:reflectiveCalls",
    "-language:existentials",
    "-language:implicitConversions"
  ),
  // Always target Java8 for maximum compatibility
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.2.14" % "test",
    "org.scalatestplus" %% "scalacheck-1-15" % "3.2.11.0" % "test",
    "com.github.scopt" %% "scopt" % "4.1.0",
    "org.json4s" %% "json4s-native" % "4.1.0-M4",
    "org.apache.commons" % "commons-text" % "1.10.0",
    "com.lihaoyi" %% "os-lib" % "0.8.1",
    "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4"
  ),
  resolvers ++= Resolver.sonatypeOssRepos("snapshots"),
  resolvers ++= Resolver.sonatypeOssRepos("releases")
)

lazy val mimaSettings = Seq(
  mimaPreviousArtifacts := Set()
)

lazy val assemblySettings = Seq(
  assembly / assemblyJarName := "firrtl.jar",
  assembly / test := {},
  assembly / assemblyOutputPath := file("./utils/bin/firrtl.jar")
)

lazy val testAssemblySettings = Seq(
  Test / assembly / test := {}, // Ditto above
  Test / assembly / assemblyMergeStrategy := {
    case PathList("firrtlTests", xs @ _*) => MergeStrategy.discard
    case x =>
      val oldStrategy = (Test / assembly / assemblyMergeStrategy).value
      oldStrategy(x)
  },
  Test / assembly / assemblyJarName := s"firrtl-test.jar",
  Test / assembly / assemblyOutputPath := file("./utils/bin/" + (Test / assembly / assemblyJarName).value)
)

lazy val antlrSettings = Seq(
  Antlr4 / antlr4GenVisitor := true,
  Antlr4 / antlr4GenListener := true,
  Antlr4 / antlr4PackageName := Option("firrtl2.antlr"),
  Antlr4 / antlr4Version := "4.9.3",
  Antlr4 / javaSource := (Compile / sourceManaged).value
)

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  Test / publishArtifact := false,
  pomIncludeRepository := { x => false },
  // scm is set by sbt-ci-release
  pomExtra := <url>http://chisel.eecs.berkeley.edu/</url>
    <licenses>
      <license>
        <name>apache_v2</name>
        <url>https://opensource.org/licenses/Apache-2.0</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <developers>
      <developer>
        <id>jackbackrack</id>
        <name>Jonathan Bachrach</name>
        <url>http://www.eecs.berkeley.edu/~jrb/</url>
      </developer>
    </developers>,
  publishTo := {
    val v = version.value
    val nexus = "https://oss.sonatype.org/"
    if (v.trim.endsWith("SNAPSHOT")) {
      Some("snapshots".at(nexus + "content/repositories/snapshots"))
    } else {
      Some("releases".at(nexus + "service/local/staging/deploy/maven2"))
    }
  }
)

lazy val docSettings = Seq(
  Compile / doc := (ScalaUnidoc / doc).value,
  autoAPIMappings := true,
  Compile / doc / scalacOptions ++= Seq(
    // ANTLR-generated classes aren't really part of public API and cause
    // errors in ScalaDoc generation
    "-skip-packages:firrtl2.antlr",
    "-Xfatal-warnings",
    "-feature",
    "-diagrams",
    "-diagrams-max-classes",
    "25",
    "-doc-version",
    version.value,
    "-doc-title",
    name.value,
    "-doc-root-content",
    baseDirectory.value + "/root-doc.txt",
    "-sourcepath",
    (ThisBuild / baseDirectory).value.toString,
    "-doc-source-url", {
      val branch =
        if (version.value.endsWith("-SNAPSHOT")) {
          "1.6.x"
        } else {
          s"v${version.value}"
        }
      s"https://github.com/chipsalliance/firrtl/tree/$branch€{FILE_PATH_EXT}#L€{FILE_LINE}"
    }
  )
)

lazy val firrtl = (project in file("."))
  .enablePlugins(ScalaUnidocPlugin)
  .enablePlugins(Antlr4Plugin)
  .settings(
    fork := true,
    Test / testForkedParallel := true
  )
  .settings(commonSettings)
  .settings(firrtlSettings)
  .settings(antlrSettings)
  .settings(assemblySettings)
  .settings(inConfig(Test)(baseAssemblySettings))
  .settings(testAssemblySettings)
  .settings(publishSettings)
  .settings(docSettings)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoPackage := "firrtl2",
    buildInfoUsePackageAsPath := true,
    buildInfoKeys := Seq[BuildInfoKey](buildInfoPackage, version, scalaVersion, sbtVersion)
  )
  .settings(mimaSettings)

lazy val bridge = (project in file("bridge"))
  .dependsOn(firrtl)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq("org.chipsalliance" %% "chisel" % "6.0.0",
      "org.apache.commons" % "commons-lang3" % "3.12.0",
      "org.apache.commons" % "commons-text" % "1.9"),
    addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % "6.0.0" cross CrossVersion.full),
  )

lazy val benchmark = (project in file("benchmark"))
  .dependsOn(firrtl)
  .settings(commonSettings)
  .settings(
    assembly / assemblyJarName := "firrtl-benchmark.jar",
    assembly / test := {},
    assembly / assemblyOutputPath := file("./utils/bin/firrtl-benchmark.jar")
  )
