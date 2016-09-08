#!/usr/bin/env bash

port=${1:-9401}

curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -d @./advancing-library-education.json \
     http://localhost:${port}/knowledge-base/instance

curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -d @./advancing-research-methods.json \
     http://localhost:${port}/knowledge-base/instance

