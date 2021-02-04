#!/bin/bash
appname=$(ls | grep .jar | sort -rn)
#appname=$(find ./ -type f -name "*.jar" | sort -rn)
name=$(echo $appname|cut -d ' ' -f1)

confName=$(ls | grep application. | sort -rn)
confFile=$(echo $confName|cut -d ' ' -f1)

java -jar \
  -Duser.timezone=GMT+08 \
  -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=15001   \
  $name \
  --spring.config.location=$confFile \
  > ./$(echo "$name" | cut -f 1 -d '.').log