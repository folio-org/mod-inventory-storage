#!/usr/bin/env bash

database_name=${1:-}
schema_name=${2:-}
admin_user_name=${3:-}
host=${4:-localhost}
port=${5:-5432}
executing_user=${6:-$USER}
executing_password=${7:-}

admin_password="admin"

./create-admin-role.sh ${admin_user_name} ${admin_password} ${host} ${port} ${executing_user} ${executing_password}
./create-db.sh ${database_name} ${admin_user_name} ${host} ${port} ${executing_user} ${executing_password}
