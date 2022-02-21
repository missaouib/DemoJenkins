#!/bin/sh
set -e
PARAM_VERSION=$1
PARAM_FILE=$2
PARAM_PATTERN=`cat $PARAM_FILE.yaml | grep ETC | grep -o '__.*' | head -n 1`
echo "PARAM_PATTERN: $PARAM_PATTERN"
BASEDIR=$(dirname "$0")
sed -i -e "s,$PARAM_PATTERN,$PARAM_VERSION,g" $BASEDIR/$PARAM_FILE.yaml
