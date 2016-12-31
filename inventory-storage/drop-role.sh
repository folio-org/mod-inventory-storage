#!/usr/bin/env bash

user_name=${1:-}
host=${2:-localhost}
port=${3:-5432}

drop_role_sql=$(cat ./drop-role.sql)
drop_role_sql="${drop_role_sql//user_name/$user_name}"

psql -h ${host} -p ${port} << EOF
${drop_role_sql}
EOF
