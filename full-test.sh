  #!/usr/bin/env bash

okapiaddress=${1:-http://localhost:9130}
kbport=${2:-9601}
catalogueport=${3:-9602}

./create-tenant.sh

./register.sh ${kbport} ${catalogueport}

gradle -Dokapi.address="${okapiaddress}" clean test testApiViaOkapi

./unregister.sh ${kbport} ${catalogueport}

./delete-tenant.sh
