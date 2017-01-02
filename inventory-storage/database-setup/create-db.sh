#!/usr/bin/env bash

database_name=${1:-}
user_name=${2:-}
host=${3:-localhost}
port=${4:-5432}
executing_user=${5:-$USER}
executing_password=${6:-}

create_database_sql=$(cat ./create-db.sql)
create_database_sql="${create_database_sql//database_name/$database_name}"
create_database_sql="${create_database_sql//user_name/$user_name}"

env PGPASSWORD=${executing_password} psql -h ${host} -p ${port} -U ${executing_user} -w << EOF
${create_database_sql}
EOF
