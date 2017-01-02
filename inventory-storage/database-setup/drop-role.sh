#!/usr/bin/env bash

user_name=${1:-}
host=${2:-localhost}
port=${3:-5432}
executing_user=${4:-$USER}
executing_password=${5:-}

drop_role_sql=$(cat ./drop-role.sql)
drop_role_sql="${drop_role_sql//user_name/$user_name}"

env PGPASSWORD=${executing_password} psql -h ${host} -p ${port} -U ${executing_user} -w << EOF
${drop_role_sql}
EOF
