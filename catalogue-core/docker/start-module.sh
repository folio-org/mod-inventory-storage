#!/bin/sh

direct_host=${1:-http://localhost:9402}

java -Dcatalogue.api.port=8080 -Dcatalogue.api.baseaddress="${direct_host}" -jar catalogue-core.jar

