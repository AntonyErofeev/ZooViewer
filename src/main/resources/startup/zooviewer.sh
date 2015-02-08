#!/bin/sh

ZOO_HOST="$1"

if [ -z "$ZOO_HOST%" ]; then
	ZOO_HOST="127.0.0.1:2181"
fi	

java -cp lib/*:zooviewer.jar -Dlog4j.configuration=log4j.properties net.isammoc.zooviewer.App "$ZOO_HOST"


