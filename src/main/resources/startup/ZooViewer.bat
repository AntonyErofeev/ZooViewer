@echo off

setlocal

set TITLE=ZooViewer - %ZOO_HOST%
title %TITLE%

javaw -cp "lib\*;zooviewer.jar" -Dlog4j.configuration=log4j.properties net.isammoc.zooviewer.App %*

endlocal