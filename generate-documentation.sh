#!/usr/bin/env bash

npm install -g raml2html
npm install -g raml2md

cd doc/api

rm -rf generated

mkdir generated
mkdir generated/html
mkdir generated/md

raml2html -i knowledgebase.raml -o generated/html/knowledgebase.html
raml2html -i catalogue.raml -o generated/html/catalogue.html

raml2md -i catalogue.raml -o generated/md/catalogue.md
raml2md -i knowledgebase.raml -o generated/md/knowledgebase.md
