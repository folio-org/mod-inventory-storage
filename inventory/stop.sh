#!/usr/bin/env bash

kill `ps -f | grep "[i]nventory.jar" | awk '{print $2}'`

