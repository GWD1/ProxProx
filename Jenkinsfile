pipeline {
  agent {
    docker {
      image 'maven:3'
      args '-v /root/.m2:/root/.m2'
    }
    
  }
  stages {
    stage('Build') {
      steps {
        sh 'mvn -U -B -DskipTests clean install'
      }
    }
    stage('Store') {
      steps {
        archiveArtifacts 'target/ProxProx.jar'
      }
    }
  }
  post {
    success {
      discordSend description: 'ProxProx Build has been succeeded: ${currentBuild.absoluteUrl}artifact/target/ProxProx.jar', footer: 'Provided with <3', link: currentBuild.absoluteUrl, successful: currentBuild.resultIsBetterOrEqualTo('SUCCESS'), title: JOB_NAME, webhookURL: 'https://discordapp.com/api/webhooks/384326195866763274/4oqtJEmf_UDcylRq7R1TUMGoSTO_U5lSwItCkssgrQBqHtNYySt-Wmxc9cme-JdOCwsB'
    }

    failure {
      discordSend description: 'ProxProx Build failed', footer: 'Provided with <3', link: currentBuild.absoluteUrl, successful: currentBuild.resultIsBetterOrEqualTo('SUCCESS'), title: JOB_NAME, webhookURL: 'https://discordapp.com/api/webhooks/384326195866763274/4oqtJEmf_UDcylRq7R1TUMGoSTO_U5lSwItCkssgrQBqHtNYySt-Wmxc9cme-JdOCwsB'
    }
  }
}
