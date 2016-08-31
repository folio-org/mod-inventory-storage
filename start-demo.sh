#!/usr/bin/env bash

./create-tenant.sh

./register.sh

./start.sh

./create-sample-data.sh

cd demo/ui

npm install

webpack-dev-server

