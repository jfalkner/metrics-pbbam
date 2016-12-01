package com.pacb.itg.metrics.pbbam

import java.nio.file.{Files, Path}

import falkner.jayson.metrics.Distribution._
import falkner.jayson.metrics.io.CSV
import falkner.jayson.metrics.{Dist, _}
import htsjdk.samtools._

import scala.collection.immutable.ListMap
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}


object PacBioBam_1_5 {
  val version = "1.5"

  // stashes the per-read metrics (probably want to export these too sometime?)
  case class Read(name: String,
                  zmw: Int,
                  len: Int,
                  avgSnrA: Float, avgSnrC: Float, avgSnrG: Float, avgSnrT: Float)
                    // base calls
//                    pulseCall: Categorical,
                    // frame counts and related measurements
//                    ipdFrames: Discrete,
//                    pulseWidthFrames: Discrete)
//                    pkMid: Discrete,
//                    pkMean: Discrete,
//                    prePulseFrames: Discrete,
//                    pulseCallWidthFrames: Discrete)

  class ReadMetric(r: Read) extends Metrics {
    override val namespace: String = "Read"
    override val version: String = "_"
    override val values: List[Metric] = List(
      Str("Name", r.name),
      Num("ZMW", r.zmw),
      Num("Avg SNR A", r.avgSnrA),
      Num("Avg SNR C", r.avgSnrC),
      Num("Avg SNR G", r.avgSnrG),
      Num("Avg SNR T", r.avgSnrT)
    )
  }

  def exportReads(reads: Seq[Read], p: Path): Unit = {
    // TODO: auto-delete on error?
    val bw = Files.newBufferedWriter(p)
    Seq(reads.head).map(r => bw.write(CSV(new ReadMetric(r)).all + "\n"))
    reads.tail.foreach(r => bw.write(CSV(new ReadMetric(r)).values + "\n"))
    bw.flush()
    bw.close()
  }

  case class Chunk(size: Long,
                   totalReadLength: Long,
                   readLength: Discrete,
                   uniqueZmws: Set[Int],
                   avgSnrA: Continuous,
                   avgSnrC: Continuous,
                   avgSnrG: Continuous,
                   avgSnrT: Continuous)

  val (snrMin, snrMax) = (0, 30)
  val (ipdFramesMin, ipdFramesMax) = (0, 210)
  val (pulseWidthFramesMin, pulseWidthFramesMax) = (0, 60)
}

/**
  * Exports PacBio specific info and metrics via single-pass through a BAM file
  *
  * See README.md for details about each metric. If you haven't read the docs, the majority of this information comes from
  * PacBioFileFormats 3.0 documentation: "BAM format specification for PacBio".
  *
  * http://pacbiofileformats.readthedocs.io/en/3.0/BAM.html
  */
abstract class PacBioBam_1_5(p: Path, nBins: Int = 30) extends Metrics {
  import PacBioBam_1_5._
  override val version = s"${PacBioBam.version}~${PacBioBam_1_5.version}"
  override val values: List[Metric] = List(
    Str("Code Version", PacBioBam.version),
    Str("Spec Version", PacBioBam_1_5.version),
    // PacBio-specific header BAM info
    Str("baz2bam Version", pr("baz2bam").getProgramVersion),
    Str("baz2bam Command Line", pr("baz2bam").getCommandLine),
    Str("bazformat Version", pr("bazformat").getProgramVersion),
    Str("bazwriter", pr("bazwriter").getProgramVersion),
    // PacBio-specific instrument and movie context
    Str("Instrument Model", rg.getPlatformModel),
    Str("Movie", rg.getPlatformUnit),
    // PacBio-specific meta-info stashed in the Read Group's Description field
    Str("Binding Kit", rgm("BINDINGKIT")),
    Str("Sequencing Kit", rgm("SEQUENCINGKIT")),
    Str("Base Caller Version", rgm("BASECALLERVERSION")),
    Num("Frame Rate", rgm("FRAMERATEHZ")),
    Num("Reads", chunks.map(_.size).sum),
    Num("Total Read Length", chunks.map(_.totalReadLength).sum),
    Dist("Read Length", mergeDiscrete(chunks.map(_.readLength))),
    Num("Unique ZMWs", chunks.map(_.uniqueZmws).flatten.toSet.size), // number of unique ZMWs
    //Dist("Mapping Quality", calcContinuous(reads.map(_.mappingQuality))),
    DistCon("Mean of SnR A Mean", mergeContinuous(chunks.map(_.avgSnrA), forceMin=Some(snrMin), forceMax=Some(snrMax))),
    DistCon("Mean of SnR C Mean", mergeContinuous(chunks.map(_.avgSnrC), forceMin=Some(snrMin), forceMax=Some(snrMax))),
    DistCon("Mean of SnR G Mean", mergeContinuous(chunks.map(_.avgSnrG), forceMin=Some(snrMin), forceMax=Some(snrMax))),
    DistCon("Mean of SnR T Mean", mergeContinuous(chunks.map(_.avgSnrT), forceMin=Some(snrMin), forceMax=Some(snrMax)))
  )

//  lazy val (header, reads): (SAMFileHeader, List[PbRead]) = Try {
//    val factory = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT)
//    val bam = factory.open(p)
//
//    (bam.getFileHeader, (for (r <- bam.iterator.asScala) yield Future(parse(r))).toList.map(r => Await.result(r, Duration.Inf)))
//  } match {
//    case Success(s) =>
//      s
//    case Failure(t) if p == null => (null, null) // support AlignedPacBioBam.blank
//    case Failure(t) => throw t
//  }

  lazy val chunkSize = 10000

  lazy val (header, chunks): (SAMFileHeader, List[Chunk]) = Try {
    val factory = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT)
    val bam = factory.open(p)

    (bam.getFileHeader, bam.iterator.asScala.grouped(chunkSize).map(g =>
      handleReads(g.map(r => Future(parse(r))).map(fr => Await.result(fr, Duration.Inf)))).toList)
      //map(c =>Future(handleReads(c))).map(fhr => Await.result(fhr, Duration.Inf)).toList)
  } match {
    case Success(s) =>
      println("Done processing.")
      println(s"Made ${s._2.size} chunks")
      s
    case Failure(t) if p == null => (null, null) // support AlignedPacBioBam.blank
    case Failure(t) => throw t
  }

  def handleReads(buf: Seq[Read]): Chunk = Chunk(
    buf.size,
    buf.map(_.len).sum, // read length
    calcDiscrete(buf.map(_.len)),
    buf.map(_.zmw).toSet, // unique ZMWs
    // SNR
    calcContinuous(buf.map(_.avgSnrA), forceMin=Some(snrMin), forceMax=Some(snrMax)),
    calcContinuous(buf.map(_.avgSnrC), forceMin=Some(snrMin), forceMax=Some(snrMax)),
    calcContinuous(buf.map(_.avgSnrG), forceMin=Some(snrMin), forceMax=Some(snrMax)),
    calcContinuous(buf.map(_.avgSnrT), forceMin=Some(snrMin), forceMax=Some(snrMax)))

  def parse(r: SAMRecord): Read = {
    val rm = r.getAttributes.asScala.map(tv => (tv.tag, tv.value)).toMap
    Read(
      r.getReadName,
      rm("zm").asInstanceOf[Int],
      r.getReadLength,
      // SNR avg per base
      rm("sn").asInstanceOf[Array[Float]](0),
      rm("sn").asInstanceOf[Array[Float]](1),
      rm("sn").asInstanceOf[Array[Float]](2),
      rm("sn").asInstanceOf[Array[Float]](3)
//      calcShort(rm("ip").asInstanceOf[Array[Short]]),
//      calcShort(rm("pw").asInstanceOf[Array[Short]])
    )
  }

  lazy val rg = header.getReadGroups.asScala.head
  lazy val rgm = rg.getDescription.split(";").map(_.split("=")).map(kv => (kv(0), kv(1))).toMap

  private def pr(key: String) : SAMProgramRecord = header.getProgramRecords.asScala.filter(_.getProgramName == key).head

  // char-base 33+ascii values for quality
  def mergeCat(reads: Seq[Read], f: (Read) => Categorical): Map[String, Int] = {
    mergeCategorical(reads.map(r => f(r)))
  }

  // make a giant histogram that summarizes all the per-read ones. gives more insight than mean/median
  def mergeDisc(buf: Seq[Read], f: (Read) => Discrete, min: Int, max: Int): Discrete = {
    mergeDiscrete(buf.map(r => f(r)).filter(_.sampleNum > 0), forceMin=Some(min), forceMax=Some(max)) // dist of interest for all reads
  }
}

class Subreads_1_5(p: Path, nBins: Int = 30) extends PacBioBam_1_5(p, nBins) with Metrics {
  override val namespace = "SUBREADS"
}

class Scraps_1_5(p: Path, nBins: Int = 30) extends PacBioBam_1_5(p, nBins) with Metrics {
  override val namespace = "SCRAPS"
}

class Unaligned_1_5(p: Path, nBins: Int = 30) extends PacBioBam_1_5(p, nBins) with Metrics {
  override val namespace = "UALIGNED"
}