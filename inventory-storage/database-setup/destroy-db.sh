#!/usr/bin/env bash

database_name=${1:-}
schema_name=${2:-}
user_name=${3:-}
host=${4:-localhost}
port=${5:-5432}
executing_user=${6:-$USER}
executing_password=${7:-}

./drop-schema.sh ${database_name} ${schema_name} ${host} ${port} ${executing_user} ${executing_password}
./drop-db.sh ${database_name} ${host} ${port} ${executing_user} ${executing_password}
./drop-role.sh ${user_name} ${host} ${port} ${executing_user} ${executing_password}
