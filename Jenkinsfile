pipeline {
  agent {
    kubernetes {
      label 'sbt'
    }
  }
  stages {
    stage('Build') {
      parallel {
        stage('server_package') {
          environment {
            appdir = "${env.WORKSPACE}"
            conffile = 'performance3.conf'
            WORK_DIR = "${env.WORKSPACE}/target/job-server"
          }

          steps {
            container('sbt-libhadoop') {
              sh 'bin/server_package.sh performance3'
            }

            archiveArtifacts artifacts: 'target/job-server/job-server.tar.gz',
                             fingerprint: true,
                             onlyIfSuccessful: true
          }
        }
        stage('job-server-tests assembly') {
          steps {
            container('sbt-libhadoop') {
              sh 'sbt job-server-tests/assembly'
            }

            archiveArtifacts artifacts: 'job-server-tests/target/**/*-assembly-*.jar',
                             fingerprint: true,
                             onlyIfSuccessful: true
          }
        }
      }
    }
  }
  post {
    success {
      emailext subject: "Build is ready: ${currentBuild.fullDisplayName}",
               body: "Get artifacts here: ${env.BUILD_URL}",
               to: 'edward.samson@stellarloyalty.com, lucky.valbuena@stellarloyalty.com'
      build job: 'test_lucky', parameters: [
        string(name: 'FROM_BUILD_NO', value: '$BUILD_NUMBER')
      ]
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
