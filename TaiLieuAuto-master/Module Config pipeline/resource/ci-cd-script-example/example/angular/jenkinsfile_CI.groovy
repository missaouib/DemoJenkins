import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import java.text.DecimalFormat
import hudson.tasks.test.AbstractTestResultAction
import groovy.json.*


//functions scan source code
def getSonarQubeAnalysisResult(sonarQubeURL, projectKey) {
    def metricKeys = "bugs,vulnerabilities,code_smells"
    def measureResp = httpRequest([
        acceptType : 'APPLICATION_JSON',
        httpMode   : 'GET',
        contentType: 'APPLICATION_JSON',
        url        : "${sonarQubeURL}/api/measures/component?metricKeys=${metricKeys}&component=${projectKey}"
    ])
    def measureInfo = jenkinsfile_utils.jsonParse(measureResp.content)
    def metricResultList = measureInfo['component']['measures']
    echo "${metricResultList}"
    int bugsEntry = getMetricEntryByKey(metricResultList, "bugs")['value'] as Integer
    int vulnerabilitiesEntry = getMetricEntryByKey(metricResultList, "vulnerabilities")['value'] as Integer
    int codeSmellEntry = getMetricEntryByKey(metricResultList, "code_smells")['value'] as Integer
    return ["bugs": bugsEntry, "vulnerabilities": vulnerabilitiesEntry, "code_smells" : codeSmellEntry ]
}

def getMetricEntryByKey(metricResultList, metricKey) {
    for (metricEntry in metricResultList) {
        if (metricEntry["metric"] == metricKey) {
            echo "${metricEntry}"
            return metricEntry
        }
    }
    return null
}

@NonCPS
def genSonarQubeProjectKey() {
    def sonarqubeProjectKey = ""
    if ("${env.gitlabActionType}".toString() == "PUSH" || "${env.gitlabActionType}".toString() == "TAG_PUSH") {
        sonarqubeProjectKey = "${env.groupName}:${env.gitlabSourceRepoName}:${env.gitlabSourceBranch}"
    } else if ("${env.gitlabActionType}".toString() == "MERGE" || "${env.gitlabActionType}".toString() == "NOTE") {
        sonarqubeProjectKey = "MR-${env.gitlabSourceRepoName}:${env.gitlabSourceBranch}-to-" +
            "${env.gitlabTargetBranch}"
    }
    return sonarqubeProjectKey.replace('/', '-')
}

//
def sonarQubeScan(buildType) {
    stage("Checkout Source Code") {
        jenkinsfile_utils.checkoutSourceCode(buildType)
        echo  'Checkout source code'
    }
    stage('SonarQube analysis') {
        env.SONAR_QUBE_PROJECT_KEY = genSonarQubeProjectKey()
        withSonarQubeEnv('SONARQ_V6'){
            sh(returnStatus: true, script:
                "/home/app/server/sonar-scanner/bin/sonar-scanner " +
                "-Dsonar.projectName=${env.SONAR_QUBE_PROJECT_KEY} " +
                "-Dsonar.projectKey=${env.SONAR_QUBE_PROJECT_KEY} " +
                "-Dsonar.java.binaries=. " +
                "-Dsonar.sources=. " +
                "-Dsonar.exclusions=**/res/**,**/target/**,**/build/**,**/share/**,**/e2e/**,**/dist/**,**/node_modules/**"
            )
                sh 'ls -al'
                sh 'cat .scannerwork/report-task.txt'
                def props = readProperties file: '.scannerwork/report-task.txt'
                env.SONAR_CE_TASK_ID = props['ceTaskId']
                env.SONAR_PROJECT_KEY = props['projectKey']
                env.SONAR_SERVER_URL = props['serverUrl']
                env.SONAR_DASHBOARD_URL = props['dashboardUrl']

                echo "SONAR_SERVER_URL: ${env.SONAR_SERVER_URL}"
                echo "SONAR_PROJECT_KEY: ${env.SONAR_PROJECT_KEY}"
                echo "SONAR_DASHBOARD_URL: ${env.SONAR_DASHBOARD_URL}"
            }
    }

     stage("3.3. Quality Gate") {
        def qg = null
        try {
            def sonarQubeRetry = 0
            def sonarScanCompleted = false
            while (!sonarScanCompleted) {
                try {
                    sleep 10
                    timeout(time: 1, unit: 'MINUTES') {
                        script {
                            qg = waitForQualityGate()
                            sonarScanCompleted = true
                            if (qg.status != 'OK') {
                                if (env.bypass == 'true') {
                                    echo "Sonar contain error"
                                }else {
                                    error "Pipeline failed due to quality gate failure: ${qg.status}"
                                }
                            }
                        }
                    }
                } catch (FlowInterruptedException interruptEx) {
                    // check if exception is system timeout
                    if (interruptEx.getCauses()[0] instanceof org.jenkinsci.plugins.workflow.steps.TimeoutStepExecution.ExceededTimeout) {
                        if (sonarQubeRetry <= 10) {
                            sonarQubeRetry += 1
                        } else {
                            if (env.bypass == 'true') {
                                echo "Sonar contain error"
                            } else {
                                error "Cannot get result from Sonarqube server. Build Failed."
                            }
                        }
                    } else {
                        throw interruptEx
                    }
                }
                catch (err) {
                    throw err
                }
            }
        }
        catch (err) {
            throw err
        } finally {
            def codeAnalysisResult = getSonarQubeAnalysisResult(env.SONAR_SERVER_URL, env.SONAR_PROJECT_KEY)
            def sonarQubeAnalysisStr = "- Vulnerabilities: <b>${codeAnalysisResult["vulnerabilities"]}</b> <br/>" +
                "- Bugs: <b>${codeAnalysisResult["bugs"]}</b> <br/>" +
                "- Code Smell: <b>${codeAnalysisResult["code_smells"]}</b> <br/>"
            def sonarQubeAnalysisComment = "<b>SonarQube Code Analysis Result: ${qg.status}</b> <br/><br/>${sonarQubeAnalysisStr} " +
                "<i><a href='${SONAR_DASHBOARD_URL}'>" +
                "Details SonarQube Code Analysis Report...</a></i><br/><br/>"
            env.SONAR_QUBE_SCAN_RESULT_STR = sonarQubeAnalysisComment
            if ("${env.gitlabActionType}".toString() == "MERGE" || "${env.gitlabActionType}".toString() == "NOTE") {
                echo "check vulnerabilities, code smell and bugs"
                int maximumAllowedVulnerabilities = env.MAXIMUM_ALLOWED_VUNERABILITIES as Integer
                int maximumAllowedBugs = env.MAXIMUM_ALLOWED_BUGS as Integer
                int maximumAllowedCodeSmell = env.MAXIMUM_ALLOWED_CODE_SMELL as Integer
                echo "maximum allow vulnerabilities:  ${maximumAllowedVulnerabilities} "
                echo "maximum allow bugs:  ${maximumAllowedBugs}"
                echo "maximum allow code smell:  ${maximumAllowedCodeSmell}"
                if (codeAnalysisResult["vulnerabilities"] > maximumAllowedVulnerabilities ||
                    codeAnalysisResult["bugs"] > maximumAllowedBugs || codeAnalysisResult["code_smells"] > maximumAllowedCodeSmell) {
                    if (env.bypass == 'true') {
                        echo "Vulnerability, code smell or bug number overs allowed limits!"
                    } else {
                        error "Vulnerability, code smell or bug number overs allowed limits!"
                    }

                }
            }
        }
    }
}
/*
    - Build all module.
    - change module to build in def buildService
*/
def buildService(buildType,enviroment) {
    stage("Checkout Source Code") {
        jenkinsfile_utils.checkoutSourceCode(buildType)
        echo  'Checkout source code'
    }
    stage('Build module front end'){
      try{
            def folder = sh(script: 'pwd', returnStdout: true)
            env.buildFolderResult = folder.trim()
            sh 'npm install'
            sh """
               npm run ${enviroment}
            """
            /* Bổ sung trong trường hợp build docker images
            sh """
               ls -la
                echo "Start build docker"
                docker build . -t ${habor}/etc/${service}:${version}
            """
           */
      } catch(err){
            error "Build Failure"
      }
    }
}
/*
    - Zip artifact build and upload to Nexus Repository
    - Config module with moduleName
    - 'check' to identify path to zip file
*/
def packageServicesAndUploadToRepo(groupId, artifactId, moduleName){
    stage("Packaging module ${moduleName}"){
        echo "Packaging zip file"
        sh """
           pwd
           cd dist
           ls -la
           zip -r ROOT.zip ROOT
        """
    }
    stage('Upload artifact to Nexus server'){
        def uploadSuccessComment = "<b>Build & package Artifact Results - " +
                                      "Build Artifact module ${moduleName} is created. "
            nexusArtifactUploader artifacts: [[artifactId: "${artifactId}_${moduleName}", classifier: '', file: "dist/ROOT.zip", type: 'zip']], credentialsId: "$env.NEXUS_CREDENTIALSID", groupId: "${groupId}", nexusUrl: '10.60.156.26:8081', nexusVersion: 'nexus3', protocol: 'http', repository: 'msbuild', version: "1.${BUILD_NUMBER}"
            env.PACKAGE_UPLOAD_IMAGE_RESULT_STR = uploadSuccessComment
    }
}
def deploy_module_web(server,groupId,artifactId){
    //http_app_port: port để check ứng dụng đã running hay chưa. Phần này sẽ bổ sung vào configFile trong ci-config trên jenkins.
    echo "deploy to server ${server}"
    sh """
        pwd
        ansible-playbook cicd/deploy/deploy_etc_batthuong-webapp_ui.yml -e http_app_port=${http_app_port} -e groupId=${groupId} -e artifactId=${artifactId} -e BUILD_NUMBER=${BUILD_NUMBER}
    """
}
/*
    - Config các stage run when push commit
    - SonarQube
    - Build
    - Deploy
*/
def buildPushCommit() {
    echo "gitlabBranch: $env.gitlabBranch"
    def versionPackage = readJSON file: 'package.json'
    def versionFrontend = versionPackage.version
    def version = "${versionFrontend}_${env.gitlabBranch}_u${BUILD_NUMBER}"
    echo " Version project : ${version}"
    env.project_version = version
    def tasks = [:]
    tasks['SonarQubeScan'] = {
        node("$env.node_slave") {
            sonarQubeScan("PUSH")
        }
    }
    tasks['Package and Build Artifact'] = {
        node("$env.node_slave") {
            stage ('Build module frontend'){
                buildService("PUSH","build")
            }
        }
    }
    parallel tasks
    def uploads = [:]
    def deploys=[:]
    if(env.gitlabBranch == env.STAGING_BRANCH){
        uploads['Push Image To Repo Nexus'] = {
            node("$env.node_slave"){
                dir("$env.buildFolderResult"){
                    stage('Packaging Module Web and Uploader To Nexus'){
                        packageServicesAndUploadToRepo("ProjectA","CI_staging","Front-End")
                        // sửa cho phù hợp. Ví dụ
                        // packageServicesAndUploadToRepo("ETC_BatThuong","CI_staging","Front-End")
                    }
                }
            }
        }
        parallel uploads
        deploys['Deploy to Server test'] = {
            node("$env.node_slave"){
               stage('Deploy to Server staging'){
                   echo "Deploy to server staging"
                    // lưu ý sửa biến cho phù hợp. Ví dụ:
                    // deploy_module_web("staging","ETC_BatThuong","CI_staging_Front-End")
                }
            }
        }
        parallel deploys
        def tests = [:]
        tests["Run Automations Test"] = {
            stage("Run Automations Test"){
                echo "Skip automations test"
            }
        }
        parallel tests
    } else {
        uploads['Push Image To Repo Nexus'] = {
            node("$env.node_slave"){
                dir("$env.buildFolderResult"){
                    stage('Packaging Module Web and Uploader To Nexus'){
                        // sửa cho phù hợp. Ví dụ
                        // packageServicesAndUploadToRepo("ETC_BatThuong","CI_staging","Front-End")
                        packageServicesAndUploadToRepo("ProjectA","CI_dev","Front-End")
                    }
                }
            }
        }
        parallel uploads

        deploys['Deploy to Server test'] = {
            node("$env.node_slave"){
                stage('Deploy to Server test'){
                    echo 'deploy server test'
                    // Sửa cho phù hợp. Ví dụ
                    // deploy_module_web("test","ETC_BatThuong","CI_dev_Front-End")
                    deploy_module_web("test","ProjectA","CI_dev_Front-End")
                }
            }
        }
        parallel deploys
    }

    currentBuild.result = "SUCCESS"
}

def buildMergeRequest() {
    echo "gitlabBranch: $env.gitlabTargetBranch"
     def versionPackage = readJSON file: 'package.json'
    def versionFrontend = versionPackage.version
    def version = "${versionFrontend}_${env.gitlabTargetBranch}_u${BUILD_NUMBER}"
    echo " Version project : ${version}"
    env.project_version = version
    def tasks = [:]
    tasks['SonarQubeScan'] = {
        node("$env.node_slave") {
            sonarQubeScan("MERGE")
        }
    }
    tasks['Package and Build Artifact'] = {
      node("$env.node_slave") {
          stage ('Build module frontend'){
              buildService("MERGE","build")
          }
      }
    }
    parallel tasks
    def uploads = [:]
    def deploys=[:]
    if(env.gitlabTargetBranch == env.STAGING_BRANCH){
        uploads['Push Image To Repo Nexus'] = {
            node("$env.node_slave"){
                dir("$env.buildFolderResult"){
                    stage('Packaging Module Web and Uploader To Nexus'){
                        // sửa cho phù hợp. Ví dụ
                        // packageServicesAndUploadToRepo("ETC_BatThuong","CI_staging","Front-End")
                        packageServicesAndUploadToRepo("ProjectA","CI_staging","Front-End")
                    }
                }
            }
        }
        parallel uploads
        deploys['Deploy to Server staging'] = {
            node("$env.node_slave"){
               stage('Deploy to Server staging'){
                    echo 'deploy to server staging'
                    // deploy_module_web("staging","ETC_BatThuong","CI_staging_Front-End")
                }
            }
        }
        parallel deploys
        def tests = [:]
        tests["Run Automations Test"] = {
            stage("Run Automations Test"){
                echo "Skip Automations test"
            }
        }
        parallel tests
    } else {
        uploads['Push Image To Repo Nexus'] = {
            node("$env.node_slave"){
                dir("$env.buildFolderResult"){
                    stage('Packaging Module Web and Uploader To Nexus'){
                        // packageServicesAndUploadToRepo("ETC_BatThuong","CI_dev","Front-End")//
                        packageServicesAndUploadToRepo("ProjectA","CI_dev","Front-End")
                    }
                }
            }
        }
        parallel uploads

        deploys['Deploy to Server test'] = {
            node("$env.node_slave"){
                stage('Deploy to Server test'){
                    echo 'deploy server'
                    deploy_module_web("test","ETC_BatThuong","CI_dev_Front-End")
                }
            }
        }
        parallel deploys
    }

    currentBuild.result = "SUCCESS"
}
return [
    buildPushCommit      : this.&buildPushCommit,
    buildMergeRequest    : this.&buildMergeRequest,
    buildAcceptAndCloseMR: this.&buildAcceptAndCloseMR

]
