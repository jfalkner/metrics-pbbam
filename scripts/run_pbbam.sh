#!/bin/bash

DEPLOY={{ deploy_dir }}

java -Xmx32G -cp $( ls $DEPLOY/lib/itg_metrics_pbbam_2.11*.jar ):$DEPLOY/lib/* com.pacb.itg.metrics.pbbam.Main $1 $2