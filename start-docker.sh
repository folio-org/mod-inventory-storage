#!/usr/bin/env bash

cd knowledge-base-core

./build-docker-image.sh

./start-docker.sh

cd ..

cd catalogue-core

./build-docker-image.sh

./start-docker.sh

cd ..

