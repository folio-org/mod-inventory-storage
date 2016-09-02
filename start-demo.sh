#!/usr/bin/env bash

npm install -g webpack

npm install -g webpack-dev-server

./create-tenant.sh

./register.sh

./start.sh

./create-sample-data.sh

cd demo/ui

npm install

webpack-dev-server

