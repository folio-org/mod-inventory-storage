#!/usr/bin/env bash

okapi_proxy_address=${1:-http://localhost:9130}

./start-with-sample-data.sh ${okapi_proxy_address}

cd demo/ui

npm install

./node_modules/.bin/webpack-dev-server --host 0.0.0.0



