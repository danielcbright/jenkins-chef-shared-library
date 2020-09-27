def call() {
  stage('Tests') {
    steps {
      wrap([$class: "$chefWrapperId", jobIdentity: "$chefJobId"]) {
        sh '/opt/chef-workstation/bin/cookstyle .'
        // sh "/opt/chef-workstation/bin/kitchen test"
        fileExists 'policy_groups.txt'
        fileExists 'Policyfile.rb'
      }
    }
  }
  stage('Build Policyfile Archive (.tgz)') {
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
}