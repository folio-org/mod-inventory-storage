#!/usr/bin/env bash

if ! command -v psql >/dev/null 2>&1; then
  echo 'Error: psql is not installed.' >&2
  exit 1
fi

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
