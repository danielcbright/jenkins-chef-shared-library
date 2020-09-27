/* groovylint-disable NestedBlockDepth */
/* groovylint-disable-next-line MethodReturnTypeRequired, MethodSize, NoDef */
def call() {
  String policyName
  String policyId
  String chefWrapperId = 'ChefIdentityBuildWrapper'
  String chefJobId = 'jenkins-dbright'
  String awsWrapperId = 'aws-policyfile-archive'
  String awsWrapperRegion = 'us-east-1'
  String s3Bucket = 'dcb-policyfile-archive'
  String azureContainerName = 'policyfile-archive'
  String azureStorageCredentialsId = 'fbc18e3a-1207-4a90-9f29-765a8b88ac86'
  String gcsCredentialsId = 'gcs-policyfile-archive'
  String gcsBucket = 'gs://policyfile-archive'
  String fileIncludePattern = '*.*'
  String toUploadDir = 'toUpload'

  pipeline {
    agent any
    parameters {
        string(defaultValue: 'NOTDEFINED', name: 'BUILD_REPOSITORY_URI', description: 'Build.Repository.Uri')
    }
    environment {
        HOME = '/root/'
    }
    stages {
      stage('Get Files from ADO') {
        steps {
          script {
            currentBuild.displayName = "${BUILD_NUMBER} - ${params.BUILD_REPOSITORY_URI}"
          }
          checkout([
            $class: 'GitSCM',
            branches: [[name: '*/master']],
            userRemoteConfigs: [[
              name: 'origin',
              credentialsId: 'jenkins-ado',
              refspec: '+refs/heads/*:refs/remotes/origin/* +refs/pull/*:refs/remotes/origin-pull/*',
              url: "${params.BUILD_REPOSITORY_URI}"
            ]],
          ])
          sh 'ls -alt'
          echo "Build.Repository.Uri: ${params.BUILD_REPOSITORY_URI}"
        }
      }
      policyOpsCommon()
      stage('Create CD Artifact') {
        steps {
          sh "echo \"$policyName:$policyId\" > policyInfo.txt"
        }
      }
    }
    post {
      always {
        archiveArtifacts artifacts: 'policyInfo.txt', onlyIfSuccessful: true
        cleanWs()
      }
    }
  }
}
