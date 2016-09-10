#!/usr/bin/env bash

docker stop `docker ps | grep "[f]olio-org-knowledge-base-core" | awk '{print $1}'`
docker rm `docker ps -a | grep "[f]olio-org-knowledge-base-core" | awk '{print $1}'`