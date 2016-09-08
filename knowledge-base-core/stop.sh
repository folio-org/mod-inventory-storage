#!/usr/bin/env bash
kill `ps -f | grep "[k]nowledge-base-core.jar" | awk '{print $2}'`


