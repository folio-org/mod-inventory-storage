#!/usr/bin/env bash

port=${1:-9402}

gradle fatJar

rm output.log

java -jar -Dcatalogue.api.port=${port} build/libs/catalogue-core.jar 1>output.log 2>output.log &

#tail -F output.log


