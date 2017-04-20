#!/usr/bin/env bash

inventory_instance_id=${1:-}
inventory_storage_instance_id=${2:-}
tenant_id=${3:-demo_tenant}

cd inventory

./stop-managed-demo.sh

cd ..

cd inventory-storage

./stop-managed-demo.sh

cd ..
