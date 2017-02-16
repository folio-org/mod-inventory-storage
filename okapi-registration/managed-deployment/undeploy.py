import requests
import sys

args = sys.argv

if(len(args) >= 3):
    module_id = args[1]
    tenant_id = args[2]
    okapi_address = args[3] or 'http://localhost:9130'
else:
    sys.stderr.write('Module and Tenant IDs must be passed on the command line')
    sys.exit()

url = '{0}/_/discovery/modules/{1}'.format(okapi_address, module_id)

instances_response = requests.get(url)

if(instances_response.status_code == 200):
    instance_ids = list(map(lambda instance: instance['instId'], instances_response.json()))

    for instance_id in instance_ids:
        instance_url = '{0}/_/discovery/modules/{1}/{2}'.format(okapi_address, module_id, instance_id)
        delete_response = requests.delete(instance_url)
        print('Delete Response for {0}: {1}'.format(instance_url, delete_response.status_code))

else:
    print('Could not enumerate instances of module {0}, status: {1}'.format(module_id, instances_response.status_code))
