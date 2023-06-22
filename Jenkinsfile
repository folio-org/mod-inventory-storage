

buildMvn {
  publishModDescriptor = 'yes'
  mvnDeploy = 'yes'
  doKubeDeploy = true
  publishPreview = false
  buildNode = 'jenkins-agent-java17'

  doDocker = {
    buildJavaDocker {
      publishPreview = false
      publishMaster = 'yes'
      // healthChk has been moved into InstallUpgradeIT.java
    }
  }
}

