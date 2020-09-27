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
    environment {
        HOME = '/root/'
    }
    stages {
      policyOpsCommon()
      stage('Kick off Publish Job') {
        steps {
          build job: 'policyfile-publish-PFP/master', propagate: false, wait: false,
          parameters: [
            string(name: 'policyName', value: "$policyName"),
            string(name: 'policyId', value: "$policyId")
          ]
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
