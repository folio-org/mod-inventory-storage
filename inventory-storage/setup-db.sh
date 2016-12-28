#!/usr/bin/env bash

database_name=${1:-}
schema_name=${2:-}
user_name=${3:-}
password=${4:-}

./drop-schema.sh ${database_name} ${schema_name}
./drop-db.sh ${database_name}
./drop-role.sh ${user_name}

./create-role.sh ${user_name} ${password}
./create-db.sh ${database_name} ${user_name}
./create-schema.sh ${database_name} ${schema_name} ${user_name}
