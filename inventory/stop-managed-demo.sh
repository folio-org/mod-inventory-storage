#!/usr/bin/env bash

inventory_instance_id=${1:-}
tenant_id=${2:-demo_tenant}

echo "Unregistering Inventory Module"
./unregister-managed.sh ${inventory_instance_id} ${tenant_id}

echo "Deleting Tenant"
./delete-tenant.sh
