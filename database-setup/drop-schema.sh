#!/usr/bin/env bash

database_name=${1:-}
schema_name=${2:-}
host=${3:-localhost}
port=${4:-5432}
executing_user=${5:-$USER}
executing_password=${6:-}

drop_schema_sql=$(cat ./drop-schema.sql)
drop_schema_sql="${drop_schema_sql//database_name/$database_name}"
drop_schema_sql="${drop_schema_sql//schema_name/$schema_name}"

env PGPASSWORD=${executing_password} psql -h ${host} -p ${port} -U ${executing_user} -w << EOF
${drop_schema_sql}
EOF
