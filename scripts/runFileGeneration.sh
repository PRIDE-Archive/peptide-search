#!/bin/sh


##### VARIABLES
# the name to give to the LSF job (to be extended with additional info)
JOB_NAME="PEPTIDE-EXPLORER"
# the job parameters that are going to be passed on to the job (build below)
JOB_PARAMETERS=""
# memory limit
MEMORY_LIMIT=40000
# LSF email notification
JOB_EMAIL="yperez@ebi.ac.uk"
LOG_FILE_NAME="${JOB_NAME}"
OUTPUT_DIRECTORY="/nfs/pride/work/data-analysis/peptide-report/results.tsv"


##### FUNCTIONS
printUsage() {
    echo "Description: File output generation for Clusters, these files will be used buy other consumers such as UNIPROT and ENSEMBL."
    echo "Usage: ./runFileGeneration.sh"
    echo "     Example: ./runFileGeneration.sh"
}


##### RUN it on the production LSF cluster #####
##### NOTE: you can change LSF group to modify the number of jobs can be run concurrently #####
bsub -M ${MEMORY_LIMIT} -R "rusage[mem=${MEMORY_LIMIT}]" -q production-rh7 -g /peptide-export -o /dev/null -J ${JOB_NAME} ./runInJava.sh ./log/${LOG_FILE_NAME}.log ${MEMORY_LIMIT}m -cp peptide-search-0.1.0-SNAPSHOT.jar uk.ac.ebi.pride.scripts.PeptideSearch "9606" ${OUTPUT_DIRECTORY}