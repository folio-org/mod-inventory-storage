#!/usr/bin/env bash

database_name=${1:-}
schema_name=${2:-}

drop_schema_sql=$(cat ./drop-schema.sql)
drop_schema_sql="${drop_schema_sql//database_name/$database_name}"
drop_schema_sql="${drop_schema_sql//schema_name/$schema_name}"

psql << EOF
${drop_schema_sql}
EOF
