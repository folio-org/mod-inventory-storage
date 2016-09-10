#!/usr/bin/env bash

cd knowledge-base-core

./stop-docker.sh

cd ..

cd catalogue-core

./stop-docker.sh

cd ..
