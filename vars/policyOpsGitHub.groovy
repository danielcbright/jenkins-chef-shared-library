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
        POLICY_GROUPS_TXT = fileExists 'policy_groups.txt'
        POLICYFILE_RB = fileExists 'Policyfile.rb'
    }
    stages {
      stage('Tests') {
        when {
          branch 'PR-*'
        }
        steps {
          wrap([$class: "$chefWrapperId", jobIdentity: "$chefJobId"]) {
            sh '/opt/chef-workstation/bin/cookstyle .'
            // sh "/opt/chef-workstation/bin/kitchen test"
          }
          script {
            if (env.POLICY_GROUPS_TXT == 'true') {
              echo 'policy_groups.txt exists...'
            } else {
              currentBuild.rawBuild.result = Result.ABORTED
              throw new hudson.AbortException('policy_groups.txt doesn\'t exist, aborting job...')
            }
            if (env.POLICYFILE_RB == 'true') {
              echo 'Policyfile.rb exists...'
            } else {
              currentBuild.rawBuild.result = Result.ABORTED
              throw new hudson.AbortException('Policyfile.rb doesn\'t exist, aborting job...')
            }
          }
        }
      }
      stage('Build Policyfile Archive (.tgz)') {
        when {
          branch 'PR-*'
        }
        steps {
          wrap([$class: "$chefWrapperId", jobIdentity: "$chefJobId"]) {
            sh '/opt/chef-workstation/bin/chef install'
            script {
              // Let's use system commands to get values to avoid using @NonCPS (thus making our pipeline
              //  serializable) We'll get the Policy information here to use in further steps
              policyId = sh (
                /* groovylint-disable-next-line LineLength */
                script: '/opt/chef-workstation/bin/chef export Policyfile.lock.json ./output -a | sed -E "s/^Exported policy \'(.*)\' to.*\\/.*-(.*)\\.tgz$/\\2/"',
                returnStdout: true
              ).trim()
              policyName = sh (
                script: "ls ./output/*$policyId* | sed -E \"s/.*\\/(.*)-.*\$/\\1/\"",
                returnStdout: true
              ).trim()
            }
            // Get rid of the Policyfile.lock.json for future runs
            sh 'rm Policyfile.lock.json'
            sh "mkdir $toUploadDir"
            sh "cp ./output/*$policyId* ./$toUploadDir/; cp ./policy_groups.txt ./$toUploadDir/"
          }
        }
      }
      stage('Upload Policyfile Archive to Remote Storage in AWS/GCP/Azure') {
        when {
          branch 'PR-*'
        }
        parallel {
          stage('Upload to GCS') {
            steps {
              dir("$toUploadDir") {
                // GCS
                googleStorageUpload(
                  credentialsId: "$gcsCredentialsId",
                  bucket: "$gcsBucket/$policyName/$policyId/",
                  pattern: "$fileIncludePattern"
                )
              }
            }
          }
          stage('Upload to S3') {
            when {
              branch 'PR-*'
            }
            steps {
              dir("$toUploadDir") {
                // S3
                withAWS(credentials: "$awsWrapperId", region: "$awsWrapperRegion") {
                  s3Upload(
                    bucket: "$s3Bucket",
                    path: "$policyName/$policyId/",
                    /* groovylint-disable-next-line DuplicateStringLiteral */
                    includePathPattern: "$fileIncludePattern"
                  )
                }
              }
            }
          }
          stage('Upload to Azure') {
            when {
              branch 'PR-*'
            }
            steps {
              dir("$toUploadDir") {
                // Azure Storage
                azureUpload(
                  storageCredentialId: "$azureStorageCredentialsId",
                  filesPath: "$fileIncludePattern",
                  storageType: 'FILE_STORAGE',
                  containerName: "$azureContainerName",
                  virtualPath: "$policyName/$policyId/"
                )
              }
            }
          }
        }
      }
      stage('Publish to Test Policy Group') {
        when {
          branch 'PR-*'
        }
        steps {
          wrap([$class: "$chefWrapperId", jobIdentity: "$chefJobId"]) {
            sh "/opt/chef-workstation/bin/chef push-archive ci-test-upload ./output/$policyName-${policyId}.tgz"
          }
        }
      }
      stage('Kick off Publish Job') {
        when {
          branch 'PR-*'
        }
        steps {
          build job: 'policyfile-publish-PFP/master', propagate: false, wait: false,
          parameters: [
            string(name: 'policyName', value: "$policyName"),
            string(name: 'policyId', value: "$policyId")
          ]
        }
      }
      stage('Create CD Artifact') {
        when {
          branch 'PR-*'
        }
        steps {
          sh "echo \"$policyName:$policyId\" > policyInfo.txt"
          archiveArtifacts artifacts: 'policyInfo.txt', onlyIfSuccessful: true
        }
      }
    }
    post {
      always {
        cleanWs()
      }
    }
  }
}
