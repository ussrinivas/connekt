name := "fk-connekt"

version := "1.0"

scalaVersion := "2.11.7"

lazy val busybees = project.in(file("busybees"))

lazy val receptors = project.in(file("receptors"))

lazy val commons = project.in(file("commons"))

lazy val root =
  project.in(file(".")).
    aggregate(busybees, receptors, commons)