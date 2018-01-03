import requests
import sys
import json

def find_module_id():
    with open('target/ModuleDescriptor.json') as descriptor_file:
        return json.load(descriptor_file)['id']

def get_instances(okapi_address, module_id):
    url = '{0}/_/discovery/modules/{1}'.format(okapi_address, module_id)

    instances_response = requests.get(url)

    if(instances_response.status_code == 200):
        return list(map(lambda instance: instance['instId'], instances_response.json()))
    else:
        print('Could not enumerate instances of module {0}, status: {1}'.format(
          module_id, instances_response.status_code))
        return list()

def un_deploy_instances():
    for instance_id in get_instances(okapi_address, module_id):
        instance_url = '{0}/_/discovery/modules/{1}/{2}'.format(
          okapi_address, module_id, instance_id)

        delete_response = requests.delete(instance_url)

        print('Delete Response for {0}: {1}'.format(
          instance_url, delete_response.status_code))

def deactivate_for_tenant(module_id, tenant_id):
    activation_url = '{0}/_/proxy/tenants/{1}/modules/{2}'.format(
        okapi_address, tenant_id, module_id)

    delete_response = requests.delete(activation_url)

    if(delete_response.status_code == 204):
        print('Module {0} deactivated for {1}'.format(module_id, tenant_id))
    else:
        print('Could not deactivate module {0}, status: {1}'.format(
            module_id, delete_response.status_code))

def remove_from_proxy(module_id):
    proxy_url = '{0}/_/proxy/modules/{1}'.format(okapi_address, module_id)

    delete_response = requests.delete(proxy_url)

    if(delete_response.status_code == 204):
        print('Module {0} removed from proxy'.format(module_id))
    else:
        print('Could not remove module {0} from proxy, status: {1}'.format(
            module_id, delete_response.status_code))

args = sys.argv

module_id = find_module_id()

if(len(args) >= 2):
    tenant_id = args[1]
    okapi_address = args[2] or 'http://localhost:9130'
else:
    sys.stderr.write('Tenant ID must be passed on the command line')
    sys.exit()

deactivate_for_tenant(module_id, tenant_id)
remove_from_proxy(module_id)
un_deploy_instances()
