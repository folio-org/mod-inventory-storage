pipeline {
   environment {
      docker_repository = 'folioci'
      docker_image = "${env.docker_repository}/mod-inventory-storage"
   }
    
   agent {
      node {
         label 'folio-jenkins-slave-docker'
      }
   }
    
   stages {
      stage('Prep') {
         steps {
            script {
               currentBuild.displayName = "#${env.BUILD_NUMBER}-${env.JOB_BASE_NAME}"
            }
                
            step([$class: 'WsCleanup'])
         }
      }
 
      stage('Checkout') {
         steps {          
            checkout([
               $class: 'GitSCM',
               branches: scm.branches,
               extensions: scm.extensions + [[$class: 'SubmoduleOption', 
                                                       disableSubmodules: false, 
                                                       parentCredentials: false, 
                                                       recursiveSubmodules: true, 
                                                       reference: '', 
                                                       trackingSubmodules: false]], 
               userRemoteConfigs: scm.userRemoteConfigs
            ])

            echo "$env.BRANCH_NAME"

         }   
      } 
        
      stage('Build') {
         steps {
            script {
               def pom = readMavenPom file: 'pom.xml'
               env.POM_VERSION = pom.version
            }

            echo "$env.POM_VERSION"

            withMaven(jdk: 'OpenJDK 8 on Ubuntu Docker Slave Node', 
                      maven: 'Maven on Ubuntu Docker Slave Node', 
                      options: [junitPublisher(disabled: false, 
                                ignoreAttachments: false), 
                                artifactsPublisher(disabled: false)]) {
                    
               sh 'mvn clean integration-test'
            }
         }
      }
        
      stage('Build Docker') {
         steps {
            script {
               docker.withRegistry('https://index.docker.io/v1/', 'DockerHubIDJenkins') {
                    
                   // def dockerImage = docker.build("${env.docker_image}:${env.POM_VERSION}-${env.BUILD_NUMBER}", '--no-cache .')
                   def dockerImage = docker.build("${env.docker_image}:${env.POM_VERSION}", '--no-cache .')
                   /* dockerImage.push()
                   dockerImage.push('latest') */
                   //sh "docker rmi ${docker_image}:${env.POM_VERSION}-${env.BUILD_NUMBER}"
                   sh "docker rmi ${docker_image}:${env.POM_VERSION}"
                   // sh "docker rmi ${docker_image}:latest"

               }
            }
         } 
      } 
   }
}
