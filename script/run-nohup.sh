#!/bin/bash
appname=$(ls | grep .jar | sort -rn)
#appname=$(find ./ -type f -name "*.jar" | sort -rn)
name=$(echo $appname|cut -d ' ' -f1)

confName=$(ls | grep application. | sort -rn)
confFile=$(echo $confName|cut -d ' ' -f1)

nohup java -jar \
  $name \
  --spring.config.location=$confFile \
#  > ./$(echo "$name" | cut -f 1 -d '.').log  \
  > ./trace.log  \
  2>&1 &