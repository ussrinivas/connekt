name := "busybees"

version := "0.1"

libraryDependencies ++= Seq(
  "org.igniterealtime.smack" % "smack-tcp" % "4.1.5",
  "org.igniterealtime.smack" % "smack-core" % "4.1.5",
  "org.igniterealtime.smack" % "smack-extensions" % "4.1.5",
  "org.isomorphism" % "token-bucket" % "1.6" excludeAll ExclusionRule("com.google.guava", "guava"),

  /** apns using pushy,  **/
  "com.relayrides" % "pushy" % "0.10-SNAPSHOT" changing(),
  "com.relayrides" % "pushy-dropwizard-metrics-listener" % "0.10-SNAPSHOT",
  "io.netty" % "netty-tcnative-boringssl-static" % "2.0.0.Final",
  "org.eclipse.jetty.alpn" % "alpn-api" % "1.1.3.v20160715",
  "org.bitbucket.b_c" % "jose4j" % "0.5.5",
  "io.jsonwebtoken" % "jjwt" % "0.7.0" % Test
  /** pushy dependecy ends **/
)


test in assembly := {}

parallelExecution in Test := false


assemblyMergeStrategy in assembly := AppBuild.mergeStrategy
