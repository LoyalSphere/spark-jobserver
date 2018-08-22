pipeline {
  agent {
    kubernetes {
      label 'sbt'
    }
  }
  stages {
    stage('server_package') {
      environment {
        appdir = "${env.WORKSPACE}"
        conffile = 'performance3.conf'
      }
      steps {
        container('sbt-libhadoop') {
          sh 'bin/server_package.sh performance3'
        }
      }
    }
  }
  post {
    success {
      mail subject: "Build is ready: ${currentBuild.fullDisplayName}",
              body: "Get artifacts here: ${env.BUILD_URL}",
              to: 'edward.samson@stellarloyalty.com'
    }

    regression {
      emailext body: "Failing: ${env.RUN_DISPLAY_URL}",
               recipientProviders: [culprits()],
               subject: "Build failure: ${currentBuild.fullDisplayName}"
    }

    fixed {
      emailext body: "Fixed: ${env.RUN_DISPLAY_URL}",
               recipientProviders: [culprits()],
               subject: "Build fixed: ${currentBuild.fullDisplayName}"
    }
  }
}