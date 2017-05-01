#!/usr/bin/env bash

database_name=${1:-}
host=${2:-localhost}
port=${3:-5432}
executing_user=${4:-$USER}
executing_password=${5:-}

drop_database_sql=$(cat ./drop-db.sql)
drop_database_sql="${drop_database_sql//database_name/$database_name}"

env PGPASSWORD=${executing_password} psql -h ${host} -p ${port} -U ${executing_user} -w << EOF
${drop_database_sql}
EOF
