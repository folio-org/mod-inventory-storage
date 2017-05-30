#!/usr/bin/env bash

npm install

./node_modules/.bin/eslint ramls/instance-storage.raml
./node_modules/.bin/eslint ramls/item-storage.raml
./node_modules/.bin/eslint ramls/loan-type.raml
./node_modules/.bin/eslint ramls/material-type.raml
