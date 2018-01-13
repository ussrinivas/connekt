import sbt.ExclusionRule

name := "commons"

version := "1.0-SNAPSHOT"

envKey := {
  System.getProperty("env.key") match {
    case null => "default"
    case defined: String => defined
  }
}


/** all akka only **/
val akkaVersion = "2.4.17"
val akkaHttpVersion = "10.0.11"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion withSources() withJavadoc(),
  "com.typesafe.akka" %% "akka-stream" % akkaVersion withSources() withJavadoc(),
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion withSources(),
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion withSources() withJavadoc(),
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test withSources() withJavadoc()
)

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % "1.0.6",
  "com.flipkart.connekt" %% "concord" % "0.2.8",
  "com.flipkart.connekt" %% "connekt-concord" % "0.2.23" excludeAll ExclusionRule(organization = "com.google.guava", name = "guava"),
  /* Logging Dependencies.Since we want to use log4j2 */
  "com.lmax" % "disruptor" % "3.3.4",
  "org.apache.logging.log4j" % "log4j-api" % "2.5",
  "org.slf4j" % "slf4j-api" % "1.7.13",
  "org.apache.logging.log4j" % "log4j-core" % "2.5",
  "org.apache.logging.log4j" % "log4j-1.2-api" % "2.5", /* For Log4j 1.x API Bridge*/
  "org.apache.logging.log4j" % "log4j-jcl" % "2.5", /* For Apache Commons Logging Bridge */
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.5", /* For SLF4J Bridge */
  /* End of Logging Libraries dependency */
  "commons-pool" % "commons-pool" % "1.6",
  "org.apache.kafka" %% "kafka" % "0.10.0.1" excludeAll(
    ExclusionRule(organization = "org.slf4j", name = "slf4j-log4j12"),
    ExclusionRule(organization = "log4j"),
    ExclusionRule("io.netty")
  ),
  "com.typesafe" % "config" % "1.3.0",
  "commons-beanutils" % "commons-beanutils" % "1.8.0",
  "org.ow2.asm" % "asm" % "4.1",
  "com.esotericsoftware" % "kryo-shaded" % "3.0.3",
  "org.apache.hbase" % "hbase-client" % "1.2.1" excludeAll(
    ExclusionRule("io.netty"),
    ExclusionRule("log4j"),
    ExclusionRule(organization = "org.slf4j"),
    ExclusionRule(organization = "asm")
  ),
  "io.netty" % "netty-all" % "4.1.14.Final",
  "org.apache.hbase" % "hbase-common" % "1.2.1" excludeAll(
    ExclusionRule("log4j"),
    ExclusionRule("io.netty"),
    ExclusionRule(organization = "org.slf4j")
  ),
  "org.apache.hadoop" % "hadoop-common" % "2.7.2" excludeAll(
    ExclusionRule("org.slf4j"),
    ExclusionRule("io.netty"),
    ExclusionRule("asm"),
    ExclusionRule("log4j"),
    ExclusionRule("org.apache.curator")
  ),
  "com.google.guava" % "guava" % "12.0.1",
  "org.scalatest" %% "scalatest" % "2.2.4" % Test,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.8.7",
  "org.skyscreamer" % "jsonassert" % "1.5.0" % Test,
  "commons-lang" % "commons-lang" % "2.6",
  "commons-pool" % "commons-pool" % "1.6",
  "org.springframework" % "spring-jdbc" % "4.2.3.RELEASE",
  "org.apache.commons" % "commons-dbcp2" % "2.1.1",
  "javax.persistence" % "persistence-api" % "1.0.2",
  "mysql" % "mysql-connector-java" % "5.1.37",
  "com.h2database" % "h2" % "1.4.187" % Test,
  "org.apache.velocity" % "velocity" % "1.7",
  "org.codehaus.groovy" % "groovy-all" % "2.4.5",
  "com.roundeights" %% "hasher" % "1.2.0",
  "com.couchbase.client" % "java-client" % "2.1.3",
  "io.reactivex" %% "rxscala" % "0.26.0",
  "org.apache.curator" % "curator-x-discovery" % "2.10.0" excludeAll(
    ExclusionRule(organization = "com.google.guava"),
    ExclusionRule(organization = "org.slf4j"),
    ExclusionRule(organization = "log4j"),
    ExclusionRule(organization = "io.netty")
    ),
  "org.apache.curator" % "curator-recipes" % "2.10.0" excludeAll(
    ExclusionRule(organization = "com.google.guava"),
    ExclusionRule(organization = "log4j"),
    ExclusionRule(organization = "org.slf4j"),
    ExclusionRule(organization = "io.netty")
  ),
  "joda-time" % "joda-time" % "2.3",
  "com.flipkart" %% "espion" % "1.0.4",
  "com.flipkart" %% "util-http" % "0.0.6",
  "commons-validator" % "commons-validator" % "1.5.0" excludeAll ExclusionRule("commons-beanutils", "commons-beanutils"),
  "org.bouncycastle" % "bcprov-jdk15on" % "1.56",
  "chronosQ" % "chronosQ-client" % "1.1-SNAPSHOT" excludeAll(
    ExclusionRule(organization = "log4j"),
    ExclusionRule(organization = "org.slf4j"),
    ExclusionRule(organization = "com.codahale.metrics"),
    ExclusionRule(organization = "org.mockito", name = "mockito-all"),
    ExclusionRule(organization = "org.hamcrest", name = "hamcrest-core")
  ),
  "chronosQ" % "chronosQ-reservation-impl-hbase" % "1.1-hdp-SNAPSHOT" excludeAll(
    ExclusionRule("org.slf4j"),
    ExclusionRule("io.netty"),
    ExclusionRule("asm"),
    ExclusionRule("log4j"),
    ExclusionRule(organization = "com.codahale.metrics"),
    ExclusionRule("org.apache.curator"),
    ExclusionRule(organization = "org.mockito", name = "mockito-all"),
    ExclusionRule(organization = "org.hamcrest", name = "hamcrest-core")
  ),
  "javax.mail" % "mail" % "1.5.0-b01",
  "net.htmlparser.jericho" % "jericho-html" % "3.3",
  "org.jsoup" % "jsoup" % "1.8.3",
  "com.googlecode.libphonenumber" % "libphonenumber" % "8.8.8",
  "com.rabbitmq" % "amqp-client" % "4.0.1",
  "net.freeutils" % "jcharset" % "2.0",
  "com.aerospike" % "aerospike-client" % "3.3.3"
)


test in assembly := {}

parallelExecution in Test := false

assemblyExcludedJars in assembly <<= (fullClasspath in assembly) map { cp =>
  cp filter {_.data.getName == "bcprov-jdk15on-1.56.jar"}
}

assemblyMergeStrategy in assembly := AppBuild.mergeStrategy
