#!/usr/bin/env bash

curl -X DELETE -D - -w '\n' http://localhost:9130/_/proxy/tenants/our

