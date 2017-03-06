#!/usr/bin/env bash

host=${1:-localhost}
port=${2:-5432}
executing_user=${3:-$USER}
executing_password=${4:-}

cd database-setup

./setup-db.sh test test_tenant_inventory_storage inventory_storage_admin ${host} ${port} ${executing_user} ${executing_password}

cd ..
