#!/usr/bin/env bash
kill `ps | grep "[k]nowledge-base-core.jar" | awk '{print $1}'`

