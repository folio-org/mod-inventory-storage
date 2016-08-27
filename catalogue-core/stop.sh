#!/usr/bin/env bash

kill `ps | grep "[c]atalogue-core.jar" | awk '{print $1}'`

