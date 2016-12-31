#!/usr/bin/env bash

host=${1:-localhost}
port=${2:-5432}

./setup-db.sh test test_tenant test_tenant ${host} ${port}