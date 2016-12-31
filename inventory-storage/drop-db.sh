#!/usr/bin/env bash

database_name=${1:-}
host=${2:-localhost}
port=${3:-5432}

drop_database_sql=$(cat ./drop-db.sql)
drop_database_sql="${drop_database_sql//database_name/$database_name}"

psql -h ${host} -p ${port} << EOF
${drop_database_sql}
EOF
