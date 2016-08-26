#!/usr/bin/env bash

./create-tenant.sh

./register.sh

./create-sample-data.sh

./start.sh

cd demo/ui

npm install

webpack-dev-server

