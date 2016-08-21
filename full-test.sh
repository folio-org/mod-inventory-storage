#!/usr/bin/env bash

./setup-okapi.sh
./register.sh

gradle clean test testOkapi