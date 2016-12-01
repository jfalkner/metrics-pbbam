# PacBio  BAM Metrics

Exports commonly used metrics from unaligned PacBio subreads from the
Sequel instrument (aka subreads.bam and scraps.bam). Useful for
characterizing the number of read producing ZMWs and the number of
subreads. The majority of these metrics are already calculated and in
the BAM file according to the "[BAM format specification for PacBio](http://pacbiofileformats.readthedocs.io/en/3.0/BAM.html)".

A non-PacBio BAM file will cause an error due to missing the various
PacBio basecalling attributes.

## Usage

This build is based on SBT and the code is intended to be used an as API.

```
# TODO example snippet showing API
```

## Metrics

The full list of metrics is listed on [itg/metrics/docs.html under the namespace PBBAM](http://itg/metrics/docs.html?q=PBBAM),
which is derived from the [Scala code in this project](src/main/scala/com/pacb/itg/metrics/pbbam/aligned/PacBioBam_1_5.scala).
