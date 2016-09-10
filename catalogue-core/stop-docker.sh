#!/usr/bin/env bash

docker stop `docker ps | grep "[f]olio-org-catalogue-core" | awk '{print $1}'`
docker rm `docker ps -a | grep "[f]olio-org-catalogue-core" | awk '{print $1}'`