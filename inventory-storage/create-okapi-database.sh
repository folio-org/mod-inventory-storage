#!/usr/bin/env bash

create_database_sql=$(cat ./okapi-setup/create-okapi-database.sql)

psql << EOF
${create_database_sql}
EOF
