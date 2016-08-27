#!/usr/bin/env bash

./create-tenant.sh

./register.sh

gradle clean test testApiViaOkapi

./unregister.sh

./delete-tenant.sh
