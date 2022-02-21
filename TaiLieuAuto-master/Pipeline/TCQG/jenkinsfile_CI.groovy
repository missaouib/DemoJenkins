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
        sonarqubeProjectKey = "${env.gitlabSourceRepoName}:${env.gitlabSourceBranch}"
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
                "-Dsonar.sources=TCMR_Web/Web " +
                "-Dsonar.exclusions=TCMR_Web/Web/bin/**/*,TCMR_Web/Web/Content/**/*,TCMR_Web/Web/obj/**/*,TCMR_Web/Web/Scripts/**/*"
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

    stage("Quality Gate") {
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
                                // change to bypass sonarque
                                // error "Pipeline failed due to quality gate failure: ${qg.status}"
                                echo "SonarQuebe Error"
                            }
                        }
                    }
                } catch (FlowInterruptedException interruptEx) {
                    // check if exception is system timeout
                    if (interruptEx.getCauses()[0] instanceof org.jenkinsci.plugins.workflow.steps.TimeoutStepExecution.ExceededTimeout) {
                        if (sonarQubeRetry <= 10) {
                            sonarQubeRetry += 1
                        } else {
                            error "Cannot get result from Sonarqube server. Build Failed."
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
            def sonarQubeAnalysisComment = "<b>SonarQube Code Analysis Result:</b> <br/><br/>${sonarQubeAnalysisStr} " +
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
                    error "Vulnerability, code smell or bug number overs allowed limits!"
                }
            }
        }
    }
}
/*
    - Build all module.
    - change module to build in def buildService
*/
def buildSerrvice(buildType) {
    stage("Checkout Source Code") {
             jenkinsfile_utils.checkoutSourceCode(buildType)
             echo  'Checkout source code'
    }
    stage('Build all module'){
        bat "D:\\VS2017\\MSBuild\\15.0\\Bin\\MSBuild.exe " + 
                "TCMR_Web\\TCMR.sln " + 
                "/p:DeployOnBuild=true /p:Configuration=Release /p:AutoParameterizationWebConfigConnectionStrings=False"
    }
}
/*
    - Zip artifact build and upload to Nexus Repository
    - Config module with moduleName
    - 'check' to identify path to zip file
*/
def packageServicesAndUploadToRepo(groupId, moduleName, check){
    if(check == true){
        stage("Packaging module ${moduleName}"){
            echo "Packaging zip file"
            bat "7z a -tzip " + 
                "${moduleName}.zip " + 
                ".\\TCMR_Web\\${moduleName}\\obj\\Release\\Package\\PackageTmp\\*"
        }
    }else {
        stage("Packaging module ${moduleName}"){
            echo "Packaging zip file"
            bat "7z a -tzip " + 
                "${moduleName}.zip " + 
                ".\\TCMR_Web\\${moduleName}\\obj\\Release\\*"
        }
    }
    stage('Upload artifact to Nexus server'){
        def uploadSuccessComment = "<b>Build & package Artifact Results - " +
                                      "Build Artifact module ${moduleName} is created. "
            nexusArtifactUploader artifacts: [[artifactId: "${groupId}_${moduleName}", classifier: '', file: "${moduleName}.zip", type: 'zip']], credentialsId: '5a87e1f9-d160-4e56-b06c-4158622898be', groupId: "${groupId}_${moduleName}", nexusUrl: '10.60.156.26:8081', nexusVersion: 'nexus3', protocol: 'http', repository: 'msbuild', version: "1.${BUILD_NUMBER}"
            env.PACKAGE_UPLOAD_IMAGE_RESULT_STR = uploadSuccessComment
    }
}
def deploy_module_web(server, groupId, moduleName){
        sh " sudo ansible-playbook deploy_to_${server}.yml -e VERSION=${BUILD_NUMBER} -e GROUPID=${groupId}_${moduleName}"
}
/*
    - Config các stage run when push commit
    - SonarQube
    - Build
    - Deploy
*/
def buildPushCommit() {
    def tasks = [:]
    tasks['SonarQubeScan'] = {
        node("slave_43") {
            dir("/u01/jenkins/workspace/TCQG/CI"){
                echo "test sonar"
                sonarQubeScan("PUSH")
            }
        }
    }
    
    tasks['Package and Build Artifact'] = {
        node("slave_203") {
            stage ('Build'){
                buildSerrvice("PUSH")
            }
            stage('Packaging Module Web and Uploader'){
                packageServicesAndUploadToRepo("TCQG_CI","Web", true)
            }
            // stage('Packaging Module Business and Uploader'){
            //     packageServicesAndUploadToRepo("TCQG_CI","Business", false)
            // }
            // stage('Packaging Module Business and Uploader'){
            //     packageServicesAndUploadToRepo("TCQG_CI","NiisApiGateway", false)
            // }
        }
    }
    parallel tasks
    stage('Deploy to server staging'){
        node('slave_43'){
            dir('/u01/jenkins/workspace/TCQG/CI'){
                deploy_module_web("staging","TCQG_CI", "Web")
            }
        }
    }
    stage('Automations Test in Staging'){
        sleep(120)
        build job: 'Autotest_TiemChung'
    }
    stage('Performance Test in Staging'){
        build job: 'autoperf_tiemchung_staging'
    }
    currentBuild.result = "SUCCESS"
}

def buildMergeRequest() {

    def stagingPublicIP = "10.60.155.244"

    def tasks = [:]
    tasks['SonarQube Scan'] = {
        node("slave_43") {
           echo "test sonar"
           sonarQubeScan("MERGE")
           
        }
    }
    tasks['Package and Build Artifact'] = {
        node("slave_203") {
            stage ('Build'){
                buildSerrvice("MERGE")
            }
            stage('Packaging Module Web and Uploader'){
                packageServicesAndUploadToRepo("TCQG_CI","Web", true)
            }
            // stage('Packaging Module Business and Uploader'){
            //     packageServicesAndUploadToRepo("TCQG_CI","", false)
            // }
        }
    }
    parallel tasks
    if (env.gitlabTargetBranch == env.STAGING_BRANCH){
        stage('Deploy to server staging'){
            node('slave_43'){
                dir('/u01/jenkins/workspace/TCQG/CI'){
                    deploy_module_web("staging","TCQG_CI", "Web")
                }
            }
        }
    }
    stage('Automations Test in Staging'){
            sleep(120)
            build job: 'Autotest_TiemChung'
    }
    stage('Performance Test in Staging'){
        build job: 'autoperf_tiemchung_staging'
    }
    currentBuild.result = "SUCCESS"
}


return [
    buildPushCommit      : this.&buildPushCommit,
    buildMergeRequest    : this.&buildMergeRequest,
    buildAcceptAndCloseMR: this.&buildAcceptAndCloseMR,
    sonarQubeScan        : this.&sonarQubeScan,
    buildSerrvice        : this.&buildSerrvice,
    deploy_module_web    : this.&deploy_module_web,
    packageServicesAndUploadToRepo: this.&packageServicesAndUploadToRepo

]
