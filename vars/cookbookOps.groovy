/* groovylint-disable NestedBlockDepth */
def call() {
  String gitHubCredId = 'github_pr'

  pipeline {
    agent any
    environment {
      HOME = '/root/'
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
          sh '/opt/chef-workstation/bin/kitchen test'
        }
      }
    }
  }
}
