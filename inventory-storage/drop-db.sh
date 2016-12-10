#!/usr/bin/env bash

database_name=${1:-}

drop_database_sql=$(cat ./drop-db.sql)
drop_database_sql="${drop_database_sql//database_name/$database_name}"

psql << EOF
${drop_database_sql}
EOF
