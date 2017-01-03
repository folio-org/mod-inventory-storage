#!/usr/bin/env bash

replace_references_in_schema() {
  schema_file=${1:-}

  rm ${schema_file}.original

  # Hack to fix references in the schema
  sed -i .original 's/\(.*ref.*\)\(instance\)\(.*\)/\1\2.json\3/g' ${schema_file}
}

replace_changed_schema_file() {
  schema_file=${1:-}

  rm ${schema_file}
  mv ${schema_file}.original ${schema_file}
}

npm install

replace_references_in_schema "inventory-storage/ramls/schema/instances.json"

./node_modules/.bin/eslint inventory-storage/ramls/instance_storage.raml

replace_changed_schema_file "inventory-storage/ramls/schema/instances.json"


