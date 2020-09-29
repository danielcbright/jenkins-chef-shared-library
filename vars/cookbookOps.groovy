/* groovylint-disable NestedBlockDepth */
def call() {
  String gitHubCredId = 'github_pr'
  String chefWrapperId = 'ChefIdentityBuildWrapper'

  pipeline {
    agent any
    environment {
      PATH = "/opt/rh/rh-ruby22/root/usr/bin:$PATH"
    }
    stages {
      stage('Cookbook Version Validation') {
        when {
          branch 'PR-*'
        }
        steps {
          script {
            withCredentials([usernamePassword(
              credentialsId: "$gitHubCredId",
              usernameVariable: 'USERNAME',
              passwordVariable: 'PASSWORD')]) {
              env.GITHUB_TOKEN = "$PASSWORD"
              env.USERNAME = "$USERNAME"
            }
          }
          cookbookOpsCompareVersions()
        }
      }
      stage('Linting (cookstyle)') {
        when {
          branch 'PR-*'
        }
        steps {
          sh '/opt/chef-workstation/bin/cookstyle .'
        }
      }
      stage('Integration Testing (kitchen test)') {
        when {
          branch 'PR-*'
        }
        steps {
          wrap([$class: "$chefWrapperId", jobIdentity: "$chefJobId"]) {
            sh '/opt/chef-workstation/bin/kitchen test'
          }
        }
      }
    }
  }
}
