#!/usr/bin/env bash

port=${1:-9401}

gradle fatJar

rm output.log

java -jar -Dknowledgebase.api.port=${port} build/libs/knowledge-base-core.jar 1>output.log 2>output.log &

#tail -F output.log


