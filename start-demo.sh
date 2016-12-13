#!/usr/bin/env bash

./create-tenant.sh

cd knowledge-base-core

./build-docker-image.sh

./start-docker.sh

cd ..

cd catalogue-core

./build-docker-image.sh

./start-docker.sh

cd ..

cd inventory-storage

./start-demo.sh

cd ..

cd inventory

./start-demo.sh

cd ..

./register.sh

./create-sample-data.sh

cd demo/ui

npm install

./node_modules/.bin/webpack-dev-server --host 0.0.0.0



