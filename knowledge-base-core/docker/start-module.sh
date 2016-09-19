#!/bin/sh

direct_host=${1:-http://localhost:9401}

java -Dknowledgebase.api.port=8080 -Dknowledgebase.api.baseaddress="${direct_host}" -jar knowledge-base-core.jar

