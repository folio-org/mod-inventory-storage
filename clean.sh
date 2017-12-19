#!/usr/bin/env bash

git clean -xdf src/ target/

mvn compile

./reset-db.sh
