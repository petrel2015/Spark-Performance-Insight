#!/bin/bash
export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"
cd /Users/user/Documents/Spark-Performance-Insight
nohup java -Dinsight.event-log-path=workspace/eventlog -jar workspace/app.jar --spring.config.additional-location=optional:file:workspace/config/application-test.yml > workspace/app.log 2>&1 &
echo $! > workspace/app.pid
