#!/usr/bin/env bash

knowledgebase_instance_id=${1:-localhost-9401}
catalogue_instance_id=${2:-localhost-9402}
inventory_instance_id=${3:-localhost-9403}
tenant_id=${4:-demo_tenant}

cd knowledge-base-core

./unregister.sh ${knowledgebase_instance_id} ${tenant_id}

cd ..

cd catalogue-core

./unregister.sh ${catalogue_instance_id} ${tenant_id}

cd ..

cd inventory

./unregister.sh ${inventory_instance_id} ${tenant_id}

cd ..
