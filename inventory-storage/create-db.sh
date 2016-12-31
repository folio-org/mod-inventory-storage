#!/usr/bin/env bash

database_name=${1:-}
user_name=${2:-}
host=${3:-localhost}
port=${4:-5432}

create_database_sql=$(cat ./create-db.sql)
create_database_sql="${create_database_sql//database_name/$database_name}"
create_database_sql="${create_database_sql//user_name/$user_name}"

psql -h ${host} -p ${port} << EOF
${create_database_sql}
EOF
