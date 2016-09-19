#!/usr/bin/env bash

knowledgebase_instance_id=${1:-localhost-9401}
catalogue_instance_id=${2:-localhost-9402}

cd knowledge-base-core

./unregister.sh ${knowledgebase_instance_id}

cd ..

cd catalogue-core

./unregister.sh ${catalogue_instance_id}

cd ..

