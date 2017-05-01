import requests

environment_variables = requests.get('http://localhost:9130/_/env')

if(environment_variables.status_code == 200):
    variable_ids = list(map(lambda variable: variable['name'], environment_variables.json()))

    for variable_id in variable_ids:
        variable_url = 'http://localhost:9130/_/env/{0}'.format(variable_id)
        delete_response = requests.delete(variable_url)
        print('Delete Response for {0}: {1}'.format(variable_url, delete_response.status_code))

else:
    print('Could not get environment variables from Okapi, status: {1}'.format(instances_response.status_code))
