#!/bin/sh
################################# Warning ##################################################
# This script shouldn't be called directly !!!
# This is used mainly to log the job output to separte file instead of LSF email directly

LOG_FILE=$1
MEMORY_LIMIT=$2
shift 2
PARAMETERS=$*

/nfs/pride/work/java/jdk1.8.0_65/bin/java -Xmx${MEMORY_LIMIT} ${PARAMETERS} > ${LOG_FILE} 2>&1
