#!/bin/bash

#set -x

### The Jira project name.  Examples: HADOOP or RIVER or LUCENE
PROJECT=$1
### The directory to accumulate the patch queue.  Must be 
### writable by this process.
QUEUE_DIR=$2
### The complete URL to trigger a build.
TRIGGER_BUILD_URL=$3

WGET=/usr/sfw/bin/wget

QUEUE_HTML_FILE=`pwd`/${PROJECT}_PatchQueue.html
CURRENT_DIR=${QUEUE_DIR}/current

CURRENT_PATCH=`cat $CURRENT_DIR/defectNum`
### If there is no current patch being tested, 
### look for the next one in the queue.
if [[ $CURRENT_PATCH == "" ]] ; then
  cd $QUEUE_DIR
  CURRENT_PATCH=`/bin/ls -1dtr ${PROJECT}* | head -1`
  ### If there is another patch in the queue, start testing it.
  if [[ $CURRENT_PATCH != "" ]] ; then
    rm -rf $CURRENT_DIR
    mv $CURRENT_PATCH $CURRENT_DIR
    ### Start build.
    echo "$CURRENT_PATCH patch submitted for testing at `date`"
    $WGET -q -O $CURRENT_DIR/build $TRIGGER_BUILD_URL
    chmod -R g+w $CURRENT_DIR
  else
    CURRENT_PATCH="none"
    CURRENT_PATCH_TIME="-"
  fi
fi
if [[ -z $CURRENT_PATCH_TIME ]] ; then
  CURRENT_PATCH_TIME=`/bin/ls -dtl ${CURRENT_DIR}* | awk '{print $6" "$7" "$8" GMT"}'`
fi

cd $QUEUE_DIR
QUEUE=`/bin/ls -1dtrl ${PROJECT}* | awk '{print "<tr><td><a href=\"http://issues.apache.org/jira/browse/"$9"\">"$9"</a></td><td>"$6" "$7" "$8" GMT</td></tr>"}'`
if [[ $QUEUE == "" ]] ; then
  QUEUE="<tr><td>empty</td><td>-</td></tr>"
fi

echo "<html>" > $QUEUE_HTML_FILE
echo "<title>Patch Queue for $PROJECT</title>" >> $QUEUE_HTML_FILE
echo "<h1>Patch Queue for $PROJECT</h1>" >> $QUEUE_HTML_FILE
echo "<hr>" >> $QUEUE_HTML_FILE
echo "<h2>Currently Running (or Waiting To Run)</h2>" >> $QUEUE_HTML_FILE
echo "<table cellspacing=10><tr align=left><th>Issue</th><th>Date Submitted to Run</th></tr>" >> $QUEUE_HTML_FILE
if [[ $CURRENT_PATCH == "none" ]] ; then
  echo "<tr><td>$CURRENT_PATCH</td><td>$CURRENT_PATCH_TIME</td></tr>" >> $QUEUE_HTML_FILE
else
  echo "<tr><td><a href=\"http://issues.apache.org/jira/browse/${CURRENT_PATCH}\">$CURRENT_PATCH</a></td><td>$CURRENT_PATCH_TIME</td></tr>" >> $QUEUE_HTML_FILE
fi
echo "</table>" >> $QUEUE_HTML_FILE
echo "<hr>" >> $QUEUE_HTML_FILE
echo "<h2>Waiting in the Queue</h2>" >> $QUEUE_HTML_FILE
echo "<table cellspacing=10><tr align=left><th>Issue</th><th>Date Submitted</th></tr>" >> $QUEUE_HTML_FILE
echo "$QUEUE" >> $QUEUE_HTML_FILE
echo "</table>" >> $QUEUE_HTML_FILE
echo "<hr>" >> $QUEUE_HTML_FILE
echo "This file last updated at: `date`" >>$QUEUE_HTML_FILE
echo "</html>" >> $QUEUE_HTML_FILE
