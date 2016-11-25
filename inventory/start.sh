#!/usr/bin/env bash

gradle fatJar

rm output.log

java -jar build/libs/inventory.jar 1>output.log 2>output.log &

printf 'Waiting for inventory module to start\n'

until $(curl --output /dev/null --silent --get --fail http://localhost:9403/inventory/items); do
    printf '.'
    sleep 1
done

printf '\n'

#tail -F output.log


