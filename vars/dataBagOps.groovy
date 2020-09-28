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
      stage('Create Data Bag(s) & Upload Contents') {
        when {
          branch 'PR-*'
        }
        steps {
          script {
            dirnames = sh (
              script: """
                dirnames=''
                for d in attribute_data_bags/*/ ; do
                  dirname="\${d%/}"
                  dirname="\${dirname##*/}"
                  dirnames+=":\${dirname}"
                fi
                echo "\${dirnames}"
                """,
              returnStdout: true
            )trim()
            dirs = dirnames.split(':')
            for (dir in dirnames) {
              def (cbURL, cbName, cbVersion) = prs.split(';')
              echo "+++ CREATING PR FOR: ${cbName} at source ${cbURL} +++"
            }
          }
          script {
          for (prs in prInfo) {
            def (cbURL, cbName, cbVersion) = prs.split(';')
            echo "+++ CREATING PR FOR: ${cbName} at source ${cbURL} +++"
          }
          wrap([$class: "$chefWrapperId", jobIdentity: "$chefJobId"]) {
            sh '''
            for d in attribute_data_bags/*/ ; do
              dirname="${d%/}"
              dirname="${dirname##*/}"
              knife data bag create ${dirname%/}
              for f in $d* ; do
                ver_on_server=`knife data bag show $dirname attributes -F json | jq -r \'.version\'`
                ver_on_disk=`jq -r \'.version\' $f`
                filename="${f%/}"
                filename="${filename##*/}"
                filename=$(echo "$filename" | cut -f 1 -d \'.\')
                mkdir -p attribute_data_bags_archive/$dirname
                echo "Version of [$dirname $f] on disk is :[$ver_on_disk], server: [$ver_on_server]"
                if [ "$ver_on_disk" -gt "$ver_on_server" ]; then
                  knife data bag show global attributes -F json > attribute_data_bags_archive/$dirname/$filename-$ver_on_server.json
                  ln -f -s $filename-$ver_on_server.json attribute_data_bags_archive/$dirname/$filename-last.json
                  knife data bag from file $dirname $f
                else
                  echo "Skipping data bag upload for $f since it\'s version on disk is not greater than the version on the server...\\n"
                fi
              done
            done
            '''
          }
          script {
            delBranches = sh (
              script: 'git branch | grep -v "master" || echo "No branches to clean up..."',
              returnStdout: true
            )trim()
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
