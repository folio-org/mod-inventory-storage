  #!/usr/bin/env bash

okapi_address=${1:-http://localhost:9130}

knowledgebase_direct_address=${2:-http://localhost:9601}
catalogue_direct_address=${3:-http://localhost:9602}

knowledgebase_instance_id=${4:-localhost-9601}
catalogue_instance_id=${5:-localhost-9602}

./create-tenant.sh

./register.sh ${knowledgebase_direct_address} ${catalogue_direct_address} ${knowledgebase_instance_id} ${catalogue_instance_id}

gradle -Dokapi.address="${okapi_address}" clean test testApiViaOkapi

./unregister.sh ${knowledgebase_instance_id} ${catalogue_instance_id}

./delete-tenant.sh
