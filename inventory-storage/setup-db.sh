#!/usr/bin/env bash

database_name=${1:-}
schema_name=${2:-}
user_name=${3:-}
host=${4:-localhost}
port=${5:-5432}

admin_user_name="inventory_storage_admin"
admin_password="admin"

./drop-schema.sh ${database_name} ${schema_name} ${host} ${port}
./drop-db.sh ${database_name} ${host} ${port}
./drop-role.sh ${user_name} ${host} ${port}

./create-admin-role.sh ${admin_user_name} ${admin_password} ${host} ${port}
./create-db.sh ${database_name} ${admin_user_name} ${host} ${port}