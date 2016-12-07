#!/usr/bin/env bash

kill `ps -f | grep "[i]nventory-storage-fat.jar" | awk '{print $2}'`

