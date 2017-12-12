#!/usr/bin/env bash

tenant=${1:-demo_tenant}

./import-reference-records.sh $tenant
./import-records.sh $tenant
