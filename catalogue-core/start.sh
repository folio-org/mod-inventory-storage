#!/usr/bin/env bash

gradle fatJar

rm output.log

java -jar build/libs/catalogue-core.jar 1>output.log 2>output.log &

#tail -F output.log


