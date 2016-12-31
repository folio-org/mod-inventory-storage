#!/usr/bin/env bash

host=${1:-localhost}
port=${2:-5432}

./setup-db.sh demo demo_tenant demo_tenant ${host} ${port}
