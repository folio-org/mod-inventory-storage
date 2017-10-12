#!/usr/bin/env bash

npm install

./node_modules/.bin/raml-cop ramls/instance-storage.raml
./node_modules/.bin/raml-cop ramls/item-storage.raml
./node_modules/.bin/raml-cop ramls/loan-type.raml
./node_modules/.bin/raml-cop ramls/material-type.raml
