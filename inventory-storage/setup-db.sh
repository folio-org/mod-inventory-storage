#!/usr/bin/env bash

database_name=${1:-}
user_name=${2:-}
password=${3:-}

./drop-db.sh ${database_name}

./drop-role.sh ${user_name}

./create-role.sh ${user_name} ${password}

./create-db.sh ${database_name} ${user_name}

psql << EOF
${drop_database_sql}
${drop_role_sql}
${create_role_sql}
${create_database_sql}
EOF
