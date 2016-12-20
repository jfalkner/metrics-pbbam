package com.pacb.itg.metrics.pbbam

import java.nio.file.Paths

import falkner.jayson.metrics.io.JSON


object Main extends App {
  if (args.size != 2)
    println("Usage: java com.pacb.itg.metrics.pbbam.Main <subreads.bam|scraps.bam> <output.json>")
  else
    JSON(Paths.get(args(1)), args(0) match {
      case b if b.endsWith("subreads.bam") => Subreads(Paths.get(b))
      case b if b.endsWith("scraps.bam") => Scraps(Paths.get(b))
    })
}
