package com.pacb.itg.metrics.pbbam

import java.nio.file.Path


object PacBioBam {

  val version = "0.0.3"
}

object Subreads {

  // version of the overall com.pacb.itg.metrics.pbbam.aligned package. should match build.sbt
  val version = PacBioBam.version

  lazy val blank = Subreads(null)

  lazy val currentVersion = blank.version

  // placeholder to support other versions down the road
  def apply(p: Path): Subreads_1_5 = new Subreads_1_5(p)
}

object Scraps {

  val version = PacBioBam.version

  lazy val blank = Scraps(null)

  lazy val currentVersion = blank.version

  def apply(p: Path): Scraps_1_5 = new Scraps_1_5(p)
}

object Unaligned {

  val version = PacBioBam.version

  lazy val blank = Unaligned(null)

  lazy val currentVersion = blank.version

  def apply(p: Path): Unaligned_1_5 = new Unaligned_1_5(p)
}