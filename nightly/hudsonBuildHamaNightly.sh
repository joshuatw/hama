#!/bin/bash

ANT_HOME=$1
FORREST_HOME=$2
FINDBUGS_HOME=$3
CLOVER_HOME=$4
PYTHON_HOME=$5
ECLIPSE_HOME=$6
SUPPORT_LIB_DIR=$7

#set -x

ulimit -n 1024

### BUILD_ID is set by Hudson
trunk=`pwd`/trunk

cd $trunk

### tar target is included in findbugs target
### run this first before instrumenting classes
$ANT_HOME/bin/ant -Dversion=$BUILD_ID -Declipse.home=$ECLIPSE_HOME -Dfindbugs.home=$FINDBUGS_HOME -Dforrest.home=$FORREST_HOME clean docs findbugs
RESULT=$?
if [ $RESULT != 0 ] ; then
  echo "Build Failed: remaining tests not run"
  exit $RESULT
fi
mv build/*.tar.gz $trunk
mv build/test/findbugs $trunk
mv build/docs/api $trunk

### clean workspace
$ANT_HOME/bin/ant clean

### Copy in any supporting jar files needed by this process
cp -r $SUPPORT_LIB_DIR/lib/* ./lib

### run checkstyle and tests with clover
$ANT_HOME/bin/ant -lib $CLOVER_HOME/lib -Dversion=$BUILD_ID -Drun.clover=true -Dpython.home=$PYTHON_HOME -Dtest.junit.output.format=xml -Dtest.output=yes -Dcompile.c++=yes checkstyle create-c++-configure test generate-clover-reports 


