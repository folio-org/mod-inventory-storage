#!/usr/bin/env bash

npm install -g raml2md
npm install -g raml2html

cd doc/api

rm -rf generated

mkdir generated
mkdir generated/md
mkdir generated/html

raml2md -i knowledgebase.raml -o generated/md/knowledgebase.md

raml2html -i knowledgebase.raml -o generated/html/knowledgebase.html


