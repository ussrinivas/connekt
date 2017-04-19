import com.typesafe.sbt.SbtStartScript

organization  := "com.flipkart.marketing"

name := "fk-pf-connekt"

version := "1.0"

parallelExecution in Test := false

assembleArtifact in assemblyPackageScala := true

assemblyJarName in assembly := "fk-pf-connekt.jar"

/** god knows why sbt requires this again here */
assemblyExcludedJars in assembly <<= (fullClasspath in assembly) map { cp =>
  cp filter { e => e.data.getName == "bcprov-jdk15on-1.56.jar" }
}

assemblyMergeStrategy in assembly :=  AppBuild.mergeStrategy

mainClass in assembly := Some("com.flipkart.connekt.boot") // fully qualified path

test in assembly := {}

seq(Revolver.settings: _*)

seq(SbtStartScript.startScriptForJarSettings: _*)

