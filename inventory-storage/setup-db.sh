#!/usr/bin/env bash

database_name=${1:-}
schema_name=${2:-}
user_name=${3:-}

admin_user_name="inventory_storage_admin"
admin_password="admin"

./drop-schema.sh ${database_name} ${schema_name}
./drop-db.sh ${database_name}
./drop-role.sh ${user_name}

./create-admin-role.sh ${admin_user_name} ${password}
./create-db.sh ${database_name} ${admin_user_name}
