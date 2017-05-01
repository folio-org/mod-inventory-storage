#!/usr/bin/env bash

user_name=${1:-}
password=${2:-}
host=${3:-localhost}
port=${4:-5432}
executing_user=${5:-$USER}
executing_password=${6:-}

create_role_sql=$(cat ./create-admin-role.sql)
create_role_sql="${create_role_sql//user_name/$user_name}"
create_role_sql="${create_role_sql//password/$password}"

env PGPASSWORD=${executing_password} psql -h ${host} -p ${port} -U ${executing_user} -w << EOF
${create_role_sql}
EOF
