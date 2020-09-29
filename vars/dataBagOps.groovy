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
                touch output.txt
                for d in attribute_data_bags/*/ ; do
                  dirname="${d%/}"
                  dirname="${dirname##*/}"
                  knife data bag create ${dirname%/}
                  for f in $d* ; do
                    filename="${f%/}"
                    filename="${filename##*/}"
                    filename=$(echo "$filename" | cut -f 1 -d '.')
                    knife data bag show $dirname $filename > output 2>&1 || :
                    if grep -q "The object you are looking for could not be found" output; then
                      ver_on_disk=`jq -r '.version' $f`
                      if [ "$ver_on_disk" == "null" ]; then
                        echo "$dirname:$f:$ver_on_disk:not_on_server:not_created_needs_version" >> output.txt
                      else
                        knife data bag from file $dirname $f
                        echo "$dirname:$f:$ver_on_disk:$ver_on_disk:created" >> output.txt
                      fi
                    else
                      ver_on_server=`knife data bag show $dirname $filename -F json | jq -r '.version'`
                      ver_on_disk=`jq -r '.version' $f`
                      mkdir -p attribute_data_bags_archive/$dirname
                      if [ "$ver_on_disk" -gt "$ver_on_server" ]; then
                        knife data bag show $dirname $filename -F json > attribute_data_bags_archive/$dirname/$filename-$ver_on_server.json
                        knife data bag from file $dirname $f
                        echo "$dirname:$f:$ver_on_disk:$ver_on_server:updated" >> output.txt
                      else
                        echo "$dirname:$f:$ver_on_disk:$ver_on_server:skipped" >> output.txt
                      fi
                    fi
                  done
                done
                ''',
                returnStdout: true
              ).trim()
              dbagStatusTxt = sh (
                script: '/usr/bin/cat output.txt',
                returnStdout: true
              ).trim()
              dbags = dbagStatusTxt.split('\n')
              for (bag in dbags) {
                vars = bag.split(':')
                bagName = "${vars[0]}"
                bagItem = "${vars[1]}"
                bagVerNew = "${vars[2]}"
                bagVerOld = "${vars[3]}"
                bagStatus = "${vars[4]}"
                echo "Data Bag: $bagName, Item: $bagItem, New Version (On Server): $bagVerNew, Old Version: $bagVerOld, Updated?: $bagStatus"
              }
            }
          }
        }
      }
      stage('Create Data Bag Artifact (backup)') {
        when {
          branch 'PR-*'
        }
        steps {
          archiveArtifacts artifacts: 'attribute_data_bags_archive/**/*', onlyIfSuccessful: true, allowEmptyArchive: true
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
