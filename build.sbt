name := "fk-connekt"

version := BuildCommons.appVersion

scalaVersion := BuildCommons.scalaVersion

crossPaths in Scope.GlobalScope := BuildCommons.crossPaths

lazy val busybees = project.in(file("busybees"))

lazy val receptors = project.in(file("receptors"))

lazy val commons = project.in(file("commons"))

lazy val root =
  project.in(file(".")).
    aggregate(busybees, receptors, commons)