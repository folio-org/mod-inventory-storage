#!/usr/bin/env bash

user_name=${1:-}
password=${2:-}

create_role_sql=$(cat ./create-role.sql)
create_role_sql="${create_role_sql//user_name/$user_name}"
create_role_sql="${create_role_sql//password/$password}"

psql << EOF
${create_role_sql}
EOF
