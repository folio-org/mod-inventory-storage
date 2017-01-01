#!/usr/bin/env bash

database_name=${1:-}
schema_name=${2:-}
user_name=${3:-}
host=${4:-localhost}
port=${5:-5432}
executing_user=${6:-}
executing_password=${7:-}

admin_user_name="inventory_storage_admin"
admin_password="admin"

./drop-schema.sh ${database_name} ${schema_name} ${host} ${port} ${executing_user} ${executing_password}
./drop-db.sh ${database_name} ${host} ${port} ${executing_user} ${executing_password}
./drop-role.sh ${user_name} ${host} ${port} ${executing_user} ${executing_password}

./create-admin-role.sh ${admin_user_name} ${admin_password} ${host} ${port} ${executing_user} ${executing_password}
./create-db.sh ${database_name} ${admin_user_name} ${host} ${port} ${executing_user} ${executing_password}