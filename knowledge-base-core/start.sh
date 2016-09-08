#!/usr/bin/env bash

gradle fatJar

rm output.log

java -jar -Dknowledgebase.api.port:9402 build/libs/knowledge-base-core.jar 1>output.log 2>output.log &

#tail -F output.log


