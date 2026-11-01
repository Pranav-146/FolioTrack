// REFERENCE ONLY: this pipeline is NOT run for this repo. CI runs on GitHub Actions (.github/workflows/ci.yml).
// It shows the equivalent Jenkins pipeline, stage for stage. To use it you'd need a Jenkins agent with Docker,
// a JDK tool named 'temurin-25', and the SonarQube Scanner plugin with a server named 'SonarCloud'
// (holding the SONAR_TOKEN credential) plus a SonarCloud webhook back to Jenkins for the quality gate.
pipeline {
    agent any

    tools {
        jdk 'temurin-25'
    }

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh './mvnw -B -DskipTests package'
            }
        }

        stage('Test') {
            steps {
                // Runs the tests and writes the JaCoCo report (target/site/jacoco)
                sh './mvnw -B verify'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                    archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true
                }
            }
        }

        stage('SonarCloud analysis') {
            steps {
                withSonarQubeEnv('SonarCloud') {
                    sh './mvnw -B sonar:sonar'
                }
            }
        }

        stage('Quality gate') {
            steps {
                // Waits for SonarCloud's webhook; a failed gate stops the pipeline before an image is built
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Docker build') {
            steps {
                sh "docker build -t foliotrack:${env.BUILD_NUMBER} ."
            }
        }
    }
}
