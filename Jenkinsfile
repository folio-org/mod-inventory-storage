

buildMvn {
  publishModDescriptor = 'yes'

  doApiDoc = true
  doApiLint = true
  apiTypes = 'RAML'
  apiDirectories = 'ramls'
  apiExcludes = 'authority-collection.raml'

  mvnDeploy = 'yes'
  doKubeDeploy = true
  publishPreview = false
  buildNode = 'jenkins-agent-java11'

  doDocker = {
    buildJavaDocker {
      publishPreview = false
      publishMaster = 'yes'
      // healthChk has been moved into InstallUpgradeIT.java
    }
  }
}

