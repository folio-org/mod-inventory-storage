#!/usr/bin/env bash

host=${1:-localhost}
port=${2:-5432}

executing_user=${4:-$USER}
executing_password=${5:-}

./destroy-demo-db.sh ${host} ${port} ${executing_user} ${executing_password}

./destroy-test-db.sh ${host} ${port} ${executing_user} ${executing_password}
