#!/usr/bin/env bash

#Run this from the root directory of the raml-module-builder source

rm -rf ~/.m2/repository/org/folio

mvn clean install

