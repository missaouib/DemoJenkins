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
@NonCPS
def getProjectCodeCoverageInfo(coverageInfoXmlStr) {
    def coverageInfoXml = jenkinsfile_utils.parseXml(coverageInfoXmlStr)
    def coverageInfoStr = ""
    coverageInfoXml.counter.each {
        def coverageType = it.@type as String
        int missed = (it.@missed as String) as Integer
        int covered = (it.@covered as String) as Integer
        int total = missed + covered

        def coveragePercent = 0.00
        if (total > 0) {
            coveragePercent = Double.parseDouble(
                new DecimalFormat("###.##").format(covered * 100.0 / total))
        }
        coverageInfoStr += "- <b>${coverageType}</b>: <i>${covered}</i>/<i>${total}</i> (<b>${coveragePercent}%</b>)<br/>"
    }
    return coverageInfoStr
}
@NonCPS
def getTestResultFromJenkins() {
    def testResult = [:]
    AbstractTestResultAction testResultAction = currentBuild.rawBuild.getAction(AbstractTestResultAction.class)
    testResult["total"] = testResultAction.totalCount
    testResult["failed"] = testResultAction.failCount
    testResult["skipped"] = testResultAction.skipCount
    testResult["passed"] = testResultAction.totalCount - testResultAction.failCount - testResultAction.skipCount
    return testResult
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
                "-Dsonar.exclusions=**/target/**,**/Libs/**,**/.mvn/**"
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
def unitTestAndCodeCoverage(buildType){
    stage("Checkout source code"){
        jenkinsfile_utils.checkoutSourceCode(buildType)
    }
    stage("Unit Test & Code Coverage"){
        try {
            sh """
            mvn clean test org.jacoco:jacoco-maven-plugin:0.8.5:report-aggregate
            """
            echo "code coverage done"
            jacoco([
                classPattern: 'target/classes',
                sourcePattern: 'src/main/java'
            ])
            def coverageResultStrComment = "<b>Coverage Test Result:</b> <br/><br/>"
            def coverageInfoXmlStr = readFile "target/jacoco-aggregate-report/jacoco.xml"
            echo "Coverage Info: ${getProjectCodeCoverageInfo(coverageInfoXmlStr)} "
            coverageResultStrComment += getProjectCodeCoverageInfo(coverageInfoXmlStr)
            coverageResultStrComment += "<i><a href='${env.BUILD_URL}Code-Coverage-Report/jacoco'>" +
                                        "Details Code Coverage Test Report...</a></i><br/><br/>"
            env.CODE_COVERAGE_RESULT_STR = coverageResultStrComment
        } catch (err) {
            echo "Error when test Unit Test"
            env.UNIT_TEST_RESULT_STR += "Error when test Unit Test"
            throw err
        } finally {
            sh 'ls -al'
            //junit '*/target/*-results/test/TEST-*.xml'
            junit 'target/surefire-reports/TEST-*.xml'
            def unitTestResult = getTestResultFromJenkins()

            env.UNIT_TEST_PASSED = unitTestResult["passed"]
            env.UNIT_TEST_FAILED = unitTestResult["failed"]
            env.UNIT_TEST_SKIPPED = unitTestResult["skipped"]
            env.UNIT_TEST_TOTAL = unitTestResult["total"]

            def testResultContent = "- Passed: <b>${unitTestResult['passed']}</b> <br/>" +
                                    "- Failed: <b>${unitTestResult['failed']}</b> <br/>" +
                                    "- Skipped: <b>${unitTestResult['skipped']}</b> <br/>"

            def testResultString =     "<b> Unit Test Result:</b> <br/><br/>${testResultContent} " +
                                    "<i><a href='${env.BUILD_URL}testReport/'>Details Unit Test Report...</a></i><br/><br/>"
            env.UNIT_TEST_RESULT_STR = testResultString

            if (unitTestResult['failed'] > 0) {
                error "Failed ${unitTestResult['failed']} unit tests"
                env.UNIT_TEST_RESULT_STR += "Failed ${unitTestResult['failed']} unit tests"
            }
        }
    }
}

/*
    - Build all module.
    - change module to build in def buildService
*/
def buildService(buildType) {
    stage("Checkout Source Code") {
        jenkinsfile_utils.checkoutSourceCode(buildType)
        echo  'Checkout source code'
    }
    stage('Build module back end'){
        try{
            def folder = sh(script: 'pwd', returnStdout: true)
            env.buildFolderResult = folder.trim()
            sh """
                mvn clean install -DskipTests
            """
        } catch(err){
            error "Build Failure"
        }

    }
}
/*
    - Config module with moduleName
    - 'check' to identify path to zip file
*/
def packageServicesAndUploadToRepo(groupId, artifactId, moduleName){

    stage('Upload artifact to Nexus server'){
        def uploadSuccessComment = "<b>Build & package Artifact Results - " +
                                      "Build Artifact module ${moduleName} is created. "
            nexusArtifactUploader artifacts: [[artifactId: "${artifactId}_${moduleName}", classifier: '', file: "target/serviceEtc-0.0.1-SNAPSHOT.jar", type: 'jar']], credentialsId: "$env.NEXUS_CREDENTIALSID", groupId: "${groupId}", nexusUrl: '10.60.156.26:8081', nexusVersion: 'nexus3', protocol: 'http', repository: 'msbuild', version: "1.${BUILD_NUMBER}"
            env.PACKAGE_UPLOAD_IMAGE_RESULT_STR = uploadSuccessComment
    }
}
def deploy_module_web(server,groupId,artifactId){
    echo "deploy to server ${server}"
    sh """
        pwd
        ansible-playbook cicd/deploy/deploy_etc_batthuong-backend.yml -e groupId=${groupId} -e artifactId=${artifactId} -e BUILD_NUMBER=${BUILD_NUMBER}
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
    def pomBackend = readMavenPom([file: "pom.xml"])
    def pomBackendVersion = pomBackend.getVersion()
    def version = "${pomBackendVersion}_${env.gitlabBranch}_u${BUILD_NUMBER}"
    echo " Version project : ${version}"
    env.project_version=version
    def tasks = [:]
    tasks['SonarQubeScan'] = {
        node("$env.node_slave") {
            sonarQubeScan("PUSH")
        }
    }
    tasks['unitTestAndCodeCoverage'] = {
        node("$env.node_slave") {
            echo "Skip unittest"
            // unitTestAndCodeCoverage("PUSH")
        }
    }
    tasks['Package and Build Artifact'] = {
        node("$env.node_slave") {
            stage ('Build jar file'){
                buildService("PUSH")
            }
        }
    }
    parallel tasks
    def uploads = [:]
    def deploys=[:]
    if(env.gitlabBranch == env.STAGING_BRANCH){
        uploads['Packaging Module Web and Uploader To Nexus'] = {
            node("$env.node_slave"){
                dir("$env.buildFolderResult"){
                    stage('Packaging Module Web and Uploader To Nexus'){
                        //Sửa lại tên artifact cho phù hợp. Ví dụ:
                        // packageServicesAndUploadToRepo("ETC_BatThuong","CI_staging","Back-End")
                        packageServicesAndUploadToRepo("ProjectA","CI_staging","Back-End")
                    }
                }
            }
        }
        parallel uploads
        deploys['Deploy to Server staging'] = {
            node("$env.node_slave"){
               stage('Deploy to Server staging'){
                    echo 'deploy server staging'
                    //sửa lại cho phù hợp. Ví dụ như sau:
                    // deploy_module_web("staging","ETC_BatThuong","CI_staging_Back-End")
                    deploy_module_web("staging","ProjectA","CI_staging_Back-End")
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
        uploads['Packaging Module Web and Uploader To Nexus'] = {
            node("$env.node_slave"){
                dir("$env.buildFolderResult"){
                    stage('Packaging Module Web and Uploader To Nexus'){
                        //sửa lại cho phù hợp. Ví dụ như sau:
                        // packageServicesAndUploadToRepo("ETC_BatThuong","CI_dev","Back-End")
                        packageServicesAndUploadToRepo("ProjectA","CI_dev","Back-End")
                    }
                }
            }
        }
        parallel uploads

        deploys['Deploy to Server test'] = {
            node("$env.node_slave"){
                stage('Deploy to Server test'){
                    echo 'deploy server'
                    //sửa lại cho phù hợp. Ví dụ như sau:
                    deploy_module_web("dev","ProjectA","CI_dev_Back-End")
                    // deploy_module_web("dev","ETC_BatThuong","CI_dev_Back-End")
                }
            }
        }
        parallel deploys
    }
    currentBuild.result = "SUCCESS"
}

def buildMergeRequest() {
    echo "gitlabTargetBranch: $env.gitlabTargetBranch"
    def pomBackend = readMavenPom([file: "pom.xml"])
    def pomBackendVersion = pomBackend.getVersion()
    def version = "${pomBackendVersion}_${env.gitlabTargetBranch}_u${BUILD_NUMBER}"
    echo " Version project : ${version}"
    env.project_version=version
    def tasks = [:]
    tasks['SonarQubeScan'] = {
        node("$env.node_slave") {
            sonarQubeScan("MERGE")
        }
    }
    tasks['unitTestAndCodeCoverage'] = {
        node("$env.node_slave") {
            echo "Skip unittest"
            // unitTestAndCodeCoverage("MERGE")
        }
    }
    tasks['Package and Build Artifact'] = {
        node("$env.node_slave") {
            stage ('Build jar file'){
                buildService("MERGE")
            }
        }

    }
    parallel tasks
    def uploads = [:]
    def deploys=[:]
    if(env.gitlabTargetBranch == env.STAGING_BRANCH){
        uploads['Packaging Module Web and Uploader To Nexus'] = {
            node("$env.node_slave"){
                dir("$env.buildFolderResult"){
                    stage('Packaging Module Web and Uploader To Nexus'){
                        //sửa lại cho phù hợp. Ví dụ
                        // packageServicesAndUploadToRepo("ETC_BatThuong","CI_staging","Back-End")
                        packageServicesAndUploadToRepo("ProjectA","CI_staging","Back-End")
                    }
                }
            }
        }
        parallel uploads
        deploys['Deploy to Server staging'] = {
            node("$env.node_slave"){
               stage('Deploy to Server staging'){
                    echo 'deploy server staging'
                    //sửa lại cho phù hợp. Ví dụ
                    // deploy_module_web("test","ETC_HauKiem","CI_staging_Back-End")
                    deploy_module_web("staging","ProjectA","CI_staging_Back-End")
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
        uploads['Push Artifact To Repo Nexus'] = {
            node("$env.node_slave"){
                dir("$env.buildFolderResult"){
                    stage('Packaging Module Web and Uploader To Nexus'){
                        //sửa lại cho phù hợp. Ví dụ
                        // packageServicesAndUploadToRepo("ETC_BatThuong","CI_dev","Back-End")
                        packageServicesAndUploadToRepo("ProjectA","CI_dev","Back-End")
                    }
                }
            }
        }
        parallel uploads

        deploys['Deploy to Server test'] = {
            node("$env.node_slave"){
                stage('Deploy to Server test'){
                    echo 'deploy server test'
                    //sửa lại cho phù hợp. Ví dụ
                    // deploy_module_web("test","ETC_BatThuong","CI_dev_Back-End")
                    deploy_module_web("test","ProjectA","CI_dev_Back-End")
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
