#!/bin/sh

java -Dknowledgebase.api.port=8080 -Dknowledgebase.api.baseaddress="http://localhost:9401" -jar knowledge-base-core.jar

