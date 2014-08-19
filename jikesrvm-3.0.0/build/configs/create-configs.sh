#!/bin/bash

# DIFC: build-time options

for i in {BaseBase,BaseAdaptive,FullAdaptive,FastAdaptive}{GenMS,MarkSweep}.properties
do
  OUT=`echo $i | sed s/.properties/_difcNoBarriers.properties/`
  cp $i $OUT
  echo config.include.difc.enabled=true >> $OUT

  OUT=`echo $i | sed s/.properties/_difc.properties/`
  cp $i $OUT
  echo config.include.difc.enabled=true >> $OUT
  echo config.include.difc.barriers=true >> $OUT

  OUT=`echo $i | sed s/.properties/_difcLibraries.properties/`
  cp $i $OUT
  echo config.include.difc.enabled=true >> $OUT
  echo config.include.difc.barriers=true >> $OUT
  echo config.include.difc.libraries=true >> $OUT

  OUT=`echo $i | sed s/.properties/_difcVerbose.properties/`
  cp $i $OUT
  echo config.include.difc.enabled=true >> $OUT
  echo config.include.difc.barriers=true >> $OUT
  echo config.include.difc.verbose=true >> $OUT

  OUT=`echo $i | sed s/.properties/_difcProfile.properties/`
  cp $i $OUT
  echo config.include.difc.enabled=true >> $OUT
  echo config.include.difc.barriers=true >> $OUT
  echo config.include.difc.profile=true >> $OUT

  OUT=`echo $i | sed s/.properties/_difcNoOptBarriers.properties/`
  cp $i $OUT
  echo config.include.difc.enabled=true >> $OUT
  echo config.include.difc.barriers=true >> $OUT
  echo config.include.difc.no-opt-barriers=true >> $OUT

  OUT=`echo $i | sed s/.properties/_difcNoRedundancyElimination.properties/`
  cp $i $OUT
  echo config.include.difc.enabled=true >> $OUT
  echo config.include.difc.barriers=true >> $OUT
  echo config.include.difc.no-redundancy-elimination=true >> $OUT

  OUT=`echo $i | sed s/.properties/_difcNoLateRedundancyElimination.properties/`
  cp $i $OUT
  echo config.include.difc.enabled=true >> $OUT
  echo config.include.difc.barriers=true >> $OUT
  echo config.include.difc.no-late-redundancy-elimination=true >> $OUT

  OUT=`echo $i | sed s/.properties/_difcNoLRENoIB.properties/`
  cp $i $OUT
  echo config.include.difc.enabled=true >> $OUT
  echo config.include.difc.barriers=true >> $OUT
  echo config.include.difc.no-late-redundancy-elimination=true >> $OUT
  echo config.include.difc.no-inlined-barriers=true >> $OUT

  OUT=`echo $i | sed s/.properties/_difcNoReadOrWriteBarriers.properties/`
  cp $i $OUT
  echo config.include.difc.enabled=true >> $OUT
  echo config.include.difc.barriers=true >> $OUT
  echo config.include.difc.no-read-or-write-barriers=true >> $OUT

  OUT=`echo $i | sed s/.properties/_difcNoRWBarrierContents.properties/`
  cp $i $OUT
  echo config.include.difc.enabled=true >> $OUT
  echo config.include.difc.barriers=true >> $OUT
  echo config.include.difc.no-rw-barrier-contents=true >> $OUT

  OUT=`echo $i | sed s/.properties/_difcNoStaticOrAllocBarriers.properties/`
  cp $i $OUT
  echo config.include.difc.enabled=true >> $OUT
  echo config.include.difc.barriers=true >> $OUT
  echo config.include.difc.no-static-or-alloc-barriers=true >> $OUT

  OUT=`echo $i | sed s/.properties/_difcNoInlinedBarriers.properties/`
  cp $i $OUT
  echo config.include.difc.enabled=true >> $OUT
  echo config.include.difc.barriers=true >> $OUT
  echo config.include.difc.no-inlined-barriers=true >> $OUT

  OUT=`echo $i | sed s/.properties/_difcNoSlowPath.properties/`
  cp $i $OUT
  echo config.include.difc.enabled=true >> $OUT
  echo config.include.difc.barriers=true >> $OUT
  echo config.include.difc.no-slow-path=true >> $OUT

  OUT=`echo $i | sed s/.properties/_difcNoSubsetChecks.properties/`
  cp $i $OUT
  echo config.include.difc.enabled=true >> $OUT
  echo config.include.difc.barriers=true >> $OUT
  echo config.include.difc.no-subset-checks=true >> $OUT

  OUT=`echo $i | sed s/.properties/_difcDynamicBarriers.properties/`
  cp $i $OUT
  echo config.include.difc.enabled=true >> $OUT
  echo config.include.difc.barriers=true >> $OUT
  echo config.include.difc.dynamic-barriers=true >> $OUT

  OUT=`echo $i | sed s/.properties/_difcDynamicBarriersLibraries.properties/`
  cp $i $OUT
  echo config.include.difc.enabled=true >> $OUT
  echo config.include.difc.barriers=true >> $OUT
  echo config.include.difc.libraries=true >> $OUT

done
