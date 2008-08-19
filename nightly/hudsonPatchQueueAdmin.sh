#!/bin/bash

set -x

### The Jira project name.  Examples: HADOOP or RIVER or LUCENE
PROJECT=$1
### The directory to accumulate the patch queue.  Must be 
### writable by this process.
QUEUE_DIR=$2

GREP=/export/home/nigel/tools/grep/bin/grep
LOG=$QUEUE_DIR/log.txt

### Scan email
while read line
do
  ### Check to see if this issue was just made "Patch Available"
  if [[ `echo $line | $GREP -c "Status: Patch Available"` == 1 ]] ; then
    patch=true
  fi
  ### Look for issue number
  if [[ `echo $line | $GREP -c "Key: $PROJECT-"` == 1 ]] ; then
    defect=`expr "$line" : ".*\(${PROJECT}-[0-9]*\)"`
    break
  fi
done

### If this email indicates a new patch, start a build
if [[ -n $patch && ! -d $QUEUE_DIR/$defect ]] ; then
  echo "$defect is being processed at `date`" >> $LOG
  mkdir $QUEUE_DIR/$defect

  ### Write the defect number to a file so buildTest.sh 
  ### knows which patch to test.
  echo $defect > $QUEUE_DIR/$defect/defectNum

  ### Since this script is run by the 'daemon' user by sendmail,
  ### make sure everything it creates is group writable
  chmod -R a+w $QUEUE_DIR/$defect
fi
exit 0
