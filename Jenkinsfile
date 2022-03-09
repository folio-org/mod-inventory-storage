

buildMvn {
  publishModDescriptor = 'yes'

  doApiDoc = true
  doApiLint = true
  apiTypes = 'RAML'
  apiDirectories = 'ramls'
  apiExcludes = 'instance-storage.raml', 'holdings-storage.raml'

  mvnDeploy = 'yes'
  runLintRamlCop = 'yes'
  doKubeDeploy = true
  publishPreview = false
  buildNode = 'jenkins-agent-java11'

  doDocker = {
    buildJavaDocker {
      publishPreview = false
      publishMaster = 'yes'
      healthChk = 'yes'
      healthChkCmd = 'curl -sS --fail -o /dev/null  http://localhost:8081/apidocs/ || exit 1'
    }
  }
}

