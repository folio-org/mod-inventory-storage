#!/usr/bin/env bash

kill `ps -f | grep "[c]atalogue-core.jar" | awk '{print $2}'`

