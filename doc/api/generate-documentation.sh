#!/usr/bin/env bash

git submodule init
git submodule update

npm install

rm -rf generated

mkdir generated
mkdir generated/html
mkdir generated/md

node_modules/raml2html/bin/raml2html -i knowledgebase.raml -o generated/html/knowledgebase.html

node_modules/raml2html/bin/raml2html -i catalogue.raml -o generated/html/catalogue.html

node_modules/raml2html/bin/raml2html -i inventory.raml -o generated/html/inventory.html

node_modules/raml2md/bin/raml2md -i knowledgebase.raml -o generated/md/knowledgebase.md

node_modules/raml2md/bin/raml2md -i catalogue.raml -o generated/md/catalogue.md

node_modules/raml2md/bin/raml2md -i inventory.raml -o generated/md/inventory.md
