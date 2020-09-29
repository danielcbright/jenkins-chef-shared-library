/* groovylint-disable GStringExpressionWithinString, LineLength, NestedBlockDepth */
/* groovylint-disable-next-line MethodReturnTypeRequired, MethodSize, NoDef */
def call() {
  String chefWrapperId = 'ChefIdentityBuildWrapper'
  String chefJobId = 'jenkins-dbright'
  
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
          wrap([$class: "$chefWrapperId", jobIdentity: "$chefJobId"]) {
            script {
              sh (
                script: '''
                for d in attribute_data_bags/*/ ; do
                  dirname="${d%/}"
                  dirname="${dirname##*/}"
                  knife data bag create ${dirname%/}
                  for f in $d* ; do
                    ver_on_server=`knife data bag show $dirname attributes -F json | jq -r '.version'`
                    ver_on_disk=`jq -r '.version' $f`
                    filename="${f%/}"
                    filename="${filename##*/}"
                    filename=$(echo "$filename" | cut -f 1 -d '.')
                    mkdir -p attribute_data_bags_archive/$dirname
                    echo "Version of [$dirname $f] on disk is :[$ver_on_disk], server: [$ver_on_server]"
                    if [ "$ver_on_disk" -gt "$ver_on_server" ]; then
                      knife data bag show global attributes -F json > attribute_data_bags_archive/$dirname/$filename-$ver_on_server.json
                      ln -f -s $filename-$ver_on_server.json attribute_data_bags_archive/$dirname/$filename-last.json
                      knife data bag from file $dirname $f
                    else
                      echo "Skipping data bag upload for $f since it's version on disk is not greater than the version on the server...\\n"
                    fi
                  done
                done
                ''',
                returnStdout: true
              )
            }
          }
        }
      }
      stage('Create Data Bag Artifact (backup)') {
        when {
          branch 'PR-*'
        }
        steps {
          archiveArtifacts artifacts: 'attribute_data_bags_archive/**/*', onlyIfSuccessful: true
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
