#!/usr/bin/env bash

gradle fatJar

mkdir ./docker/tmp

cp ./build/libs/catalogue-core.jar ./docker/tmp

cp ./docker/Dockerfile ./docker/tmp
cp ./docker/start-module.sh ./docker/tmp

docker build -t folio-org-catalogue-core ./docker/tmp

rm -rf ./docker/tmp

cd ..

