#!/usr/bin/env bash

user_name=${1:-}

drop_role_sql=$(cat ./drop-role.sql)
drop_role_sql="${drop_role_sql//user_name/$user_name}"

psql << EOF
${drop_role_sql}
EOF
