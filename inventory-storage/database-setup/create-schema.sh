#!/usr/bin/env bash

database_name=${1:-}
schema_name=${2:-}
user_name=${3:-}

create_schema_sql=$(cat ./create-schema.sql)
create_schema_sql="${create_schema_sql//database_name/$database_name}"
create_schema_sql="${create_schema_sql//schema_name/$schema_name}"
create_schema_sql="${create_schema_sql//user_name/$user_name}"

psql << EOF
${create_schema_sql}
EOF
