#!/usr/bin/env bash

knowledgebase_instance_id=${1:-localhost-9401}
catalogue_instance_id=${2:-localhost-9402}

./stop-docker.sh

./unregister.sh ${knowledgebase_instance_id} ${catalogue_instance_id}

./delete-tenant.sh
