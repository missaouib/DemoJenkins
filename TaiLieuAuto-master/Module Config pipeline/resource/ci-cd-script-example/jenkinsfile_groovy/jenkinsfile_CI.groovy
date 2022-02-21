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
                "-Dsonar.exclusions=**/target/**,**/Libs/**"
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
            mvn clean test org.jacoco:jacoco-maven-plugin:0.8.2:report-aggregate
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
def buildService(buildType, version) {
    stage("Checkout Source Code") {
        jenkinsfile_utils.checkoutSourceCode(buildType)
        echo  'Checkout source code'
    }
    stage('Build back end'){
       def dir = sh(script: 'pwd', returnStdout: true)
       env.buildFolderResult = dir.trim()
       sh """
            mvn clean install
            find target/ -name "*.jar" -print0 | xargs -0 cp -t docker/
            cd docker
            ls -la
            docker build . -t 10.60.156.72/etc/crm-backend:${version}
       """
    }
    /*cấu hình cho build trên window server
       run trên node "slave_203"
    */
    // stage("Checkout Source Code") {
    //          jenkinsfile_utils.checkoutSourceCode(buildType)
    //          echo  'Checkout source code'
    // }
    // stage('Build all module'){
    //     bat "D:\\VS2017\\MSBuild\\15.0\\Bin\\MSBuild.exe " +
    //             "SParent.sln " +
    //             "/T:Clean;Build /p:Configuration=Release /p:AutoParameterizationWebConfigConnectionStrings=False /p:DeployOnBuild=true"
    // }
}

/*
    - Config module with moduleName
    - 'check' to identify path to zip file
*/
// example for linux
def packageServicesAndUploadToRepo(groupId, artifactId, moduleName){

    stage('Upload artifact to Nexus server'){
        def uploadSuccessComment = "<b>Build & package Artifact Results - " +
                                      "Build Artifact module ${moduleName} is created. "
            nexusArtifactUploader   artifacts: [[ artifactId: "${artifactId}_${moduleName}",
                                                classifier: '',
                                                file: "{đường dẫn file kết quả build}",
                                                type: '{nhập type file artifact}']],
                                    credentialsId: 'CredentailID-nexus',
                                    groupId: "${groupId}",
                                    nexusUrl: '10.60.156.26:8081',
                                    nexusVersion: 'nexus3',
                                    protocol: 'http',
                                    repository: 'msbuild',
                                    version: "1.${BUILD_NUMBER}"
            env.PACKAGE_UPLOAD_IMAGE_RESULT_STR = uploadSuccessComment
    }
}
// example for windows
def packageServicesAndUploadToRepo(groupId, artifactId, moduleName){
    stage("Remove file DB dll and file config SMAS Web"){
        echo "Packaging zip file"
        bat """
           del ".\\1. Web\\SMAS.Web\\obj\\Release\\Package\\PackageTmp\\bin\\Oracle.DataAccess.dll" ".\\1. Web\\SMAS.Web\\obj\\Release\\Package\\PackageTmp\\Web.config" ".\\1. Web\\SMAS.Web\\obj\\Release\\Package\\PackageTmp\\bin\\System.Data.SQLite.dll"
        """
    }
    stage("Packaging module Smas Web"){
        echo "Packaging zip file"
         bat "7z a -tzip " +
                "${groupId}_${artifactId}.zip " +
                '\".\\1. Web\\SMAS.Web\\obj\\Release\\Package\\PackageTmp\\*\"'
    }
    stage('Upload artifact Smas Web to Nexus server'){
        def uploadSuccessComment = "<b>Build & package Artifact Results - " +
                                      "Build Artifact module ${moduleName} is created. "
            nexusArtifactUploader artifacts: [[artifactId: "${artifactId}", classifier: '', file: "${groupId}_${artifactId}.zip", type: 'zip']], credentialsId: 'bc9e98f9-72f8-49a7-8c63-1081210daa3c', groupId: "${groupId}", nexusUrl: '10.60.156.26:8081', nexusVersion: 'nexus3', protocol: 'http', repository: 'msbuild', version: "1.${BUILD_NUMBER}"
            env.PACKAGE_UPLOAD_IMAGE_RESULT_STR = uploadSuccessComment
    }
}

def pushImageToDockerRepo(version){
    stage ("Login to Docker Repository"){
        withCredentials([
            usernamePassword(credentialsId: 'Credential-login-habor',
                            usernameVariable: 'username',
                            passwordVariable: 'password')
        ]){
            sh """
                docker --config ~/.docker/.[đặt tên folder credential] \
                login -u ${username} -p '${password}' 10.60.156.72
            """
        }
    }
    stage ("Push image to Docker Repository"){
        sh """
            docker --config ~/.docker/.[đặt tên folder credential] \
            push 10.60.156.72/{nhập tên namespace}/{Nhập tên service}:${version}
            docker rmi 10.60.156.72/{nhập tên namespace}/{Nhập tên service}:${version}
        """
    }
}
// deploy for linux server
def deploy_module_web(server,groupId,artifactId){
    echo "deploy to server ${server}"
    sh """
        pwd
        ansible-playbook cicd/deploy/deploy_file_${server}.yml \
        -e groupId=${groupId} \
        -e artifactId=${artifactId} \
        -e BUILD_NUMBER=${BUILD_NUMBER}
    """
}
// deploy for window server
def deploy_module_web(server, groupId, artifactId){
    echo "deploy to server ${server}"
    sh " sudo ansible-playbook deploy_smas_${server}.yml -e VERSION=${BUILD_NUMBER} -e GROUP_ID=${groupId} -e ARTIFACT_ID=${artifactId}"
}

// scan url with acunetix
def acunetixScan(url){
    sleep(30)
    def target_id = null
    withCredentials([string(credentialsId: 'acunetix-api-token', variable: 'accunetix')]) {
        // get info scan list in acunetix
        def scanListsResults = httpRequest([
            acceptType   : 'APPLICATION_JSON',
            httpMode     : 'GET',
            contentType  : 'APPLICATION_JSON',
            customHeaders: [[name: "X-Auth", value: accunetix]],
            url          : "${env.ACUNETIX_API_URL}/scans",
            ignoreSslErrors: true
        ])
        // get target ID with address link project
        for (scanListsResult in jenkinsfile_utils.jsonParse(scanListsResults.content)['scans']) {
            if(scanListsResult['target']['address'] == "http://${url}"){
                println "Target added get target ID to delete target"
                def target_id_remove=scanListsResult['target_id']
                // delete target with targetID in accunetix
                def removeTarget = httpRequest([
                    acceptType   : 'APPLICATION_JSON',
                    httpMode     : 'DELETE',
                    contentType  : 'APPLICATION_JSON',
                    customHeaders: [[name: "X-Auth", value: accunetix]],
                    url          : "${env.ACUNETIX_API_URL}/targets/${target_id_remove}",
                    ignoreSslErrors: true
                ])

            }
        }
        // content target json
        def targetContentJson = """
            {
                "address": "http://${url}",
                "description": "null",
                "criticality": "10"
            }
        """
        // add target to acunetix
        def addTarget= httpRequest([
            acceptType   : 'APPLICATION_JSON',
            httpMode     : 'POST',
            contentType  : 'APPLICATION_JSON',
            customHeaders: [[name: "X-Auth", value: accunetix]],
            url          : "${env.ACUNETIX_API_URL}/targets",
            requestBody  : targetContentJson,
            ignoreSslErrors: true
        ])
        // get target_id after add
        target_id= jenkinsfile_utils.jsonParse(addTarget.content)['target_id']
        println ("Result Add Target : " + jenkinsfile_utils.jsonParse(addTarget.content)['target_id'])

        // data scan
        def scanContentJson= """
            {
                "profile_id":"11111111-1111-1111-1111-111111111111",
                "incremental":false,
                "schedule":{
                    "disable":false,
                    "start_date":null,
                    "time_sensitive":false
                },
                "target_id":"$target_id"
            }
        """
        // "report_template_id":"11111111-1111-1111-1111-111111111111",
        // scan target
        def scan = httpRequest([
            acceptType   : 'APPLICATION_JSON',
            httpMode     : 'POST',
            contentType  : 'APPLICATION_JSON',
            customHeaders: [[name: "X-Auth", value: accunetix]],
            url          : "${env.ACUNETIX_API_URL}/scans",
            requestBody  : scanContentJson,
            timeout: 5,
            ignoreSslErrors: true
        ])
        print("Status: " + jenkinsfile_utils.jsonParse(scan.content))
        def scanAcunetixRetry = 0
        def scanAcunetixCompleted = false
        def scan_id
        //variable to generate report
        def last_scan_session_id
        def getResults
        while (!scanAcunetixCompleted) {
            try {
                timeout(time: 1, unit: 'MINUTES') {
                    script {
                        // request search target to get status
                        def searchResultByTarget = httpRequest([
                            acceptType   : 'APPLICATION_JSON',
                            httpMode     : 'GET',
                            contentType  : 'APPLICATION_JSON',
                            customHeaders: [[name: "X-Auth", value: accunetix]],
                            url          : "${env.ACUNETIX_API_URL}/targets?l=20&q=text_search:*${url}",
                            ignoreSslErrors: true
                        ])
                        def targets = jenkinsfile_utils.jsonParse(searchResultByTarget.content)['targets']
                            for(target in targets){
                                println ("target: " + target)
                                def results = target['last_scan_session_status']
                                    println("Result: "+results)
                                    if( results== "completed"){
                                        scanAcunetixCompleted = true
                                        scan_id = target['last_scan_id']
                                        println ("scan_id: " + scan_id)
                                        last_scan_session_id = target['last_scan_session_id']
                                        println ("last_scan_session_id: "+ last_scan_session_id)
                                        // get result scan target
                                        getResults = httpRequest([
                                            acceptType   : 'APPLICATION_JSON',
                                            httpMode     : 'GET',
                                            contentType  : 'APPLICATION_JSON',
                                            customHeaders: [[name: "X-Auth", value: accunetix]],
                                            url          : "${env.ACUNETIX_API_URL}/scans/$scan_id",
                                            ignoreSslErrors: true
                                        ])
                                        println ("Result: " + jenkinsfile_utils.jsonParse(getResults.content))
                                    }
                            }
                    }
                }
            } catch (FlowInterruptedException interruptEx) {
                // check if exception is system timeout
                if (interruptEx.getCauses()[0] instanceof org.jenkinsci.plugins.workflow.steps.TimeoutStepExecution.ExceededTimeout) {
                    if (scanAcunetixRetry <= 10) {
                        scanAcunetixRetry += 1
                    } else {
                        error "Cannot get result from acunetix server. Build Failed."
                        }
                } else {
                    throw interruptEx
                }
            }
            catch (err) {
                throw err
            }
        }
        def severity_counts_high = jenkinsfile_utils.jsonParse(getResults.content)['current_session']['severity_counts']['high']
        println ("severity_counts_high: " + severity_counts_high)
        def severity_counts_info = jenkinsfile_utils.jsonParse(getResults.content)['current_session']['severity_counts']['info']
        println ("severity_counts_info: " + severity_counts_info)
        def severity_counts_low = jenkinsfile_utils.jsonParse(getResults.content)['current_session']['severity_counts']['low']
        println ("severity_counts_low: " + severity_counts_low)
        def severity_counts_medium = jenkinsfile_utils.jsonParse(getResults.content)['current_session']['severity_counts']['medium']
        println ("severity_counts_medium: " + severity_counts_medium)

        env.SECURITY_ALERT_HIGH = severity_counts_high
        env.SECURITY_ALERT_INFO = severity_counts_info
        env.SECURITY_ALERT_LOW = severity_counts_low
        env.SECURITY_ALERT_MEDIUM= severity_counts_medium

        // config data for generate report
        def reportContentJson = """
            {
                "template_id":"11111111-1111-1111-1111-111111111111",
                "source":
                    {
                        "list_type":"scan_result",
                        "id_list":["$last_scan_session_id"]
                    }
            }
        """
        println ("reportContentJson : " +reportContentJson)

        def folder = sh(returnStdout: true, script :'pwd').trim()
        def reportId=""
        dir("$folder"){
            def genReports = sh(returnStdout: true, script : """
            curl -i -d '$reportContentJson' '${env.ACUNETIX_API_URL}/reports' \
            -H 'Content-Type: application/json;charset=utf8' -H 'X-Auth: $accunetix' \
            --insecure -o responeHeader.txt
            """).trim().tokenize("\n")
            reportId = sh(returnStdout: true, script: 'cat responeHeader.txt | grep \'Location:\' | sed \'s/.*://\' | sed \'s/.*\\///\'')
            println("ReportID: " + reportId)
        }
        def genReportCompleted = false
        def genReportRetry = 0

        while (!genReportCompleted) {
            try {
                sleep 10
                timeout(time: 1, unit: 'MINUTES') {
                    script {
                        def getLinkDownload = httpRequest([
                            acceptType   : 'APPLICATION_JSON',
                            httpMode     : 'GET',
                            contentType  : 'APPLICATION_JSON',
                            customHeaders: [[name: "X-Auth", value: accunetix]],
                            url          : "${env.ACUNETIX_API_URL}/reports/$reportId",
                            ignoreSslErrors: true
                        ])
                        def resultGenReport = jenkinsfile_utils.jsonParse(getLinkDownload.content)
                        if(resultGenReport['status'] == "completed"){
                            genReportCompleted = true
                            def downloadLink = resultGenReport['download'][0]
                            println downloadLink
                            def getFileDownload = httpRequest([
                                acceptType   : 'APPLICATION_JSON',
                                httpMode     : 'GET',
                                contentType  : 'APPLICATION_JSON',
                                customHeaders: [[name: "X-Auth", value: accunetix]],
                                url          : "${env.ACUNETIX_URL}/$downloadLink",
                                ignoreSslErrors: true
                            ])
                            writeFile file: 'fileScanResult.html', text: getFileDownload.content
                            stash name: "fileScanResult.html", includes: "fileScanResult.html"
                            publishHTML([
                                allowMissing         : false,
                                alwaysLinkToLastBuild: false,
                                keepAll              : true,
                                reportDir            : './',
                                reportFiles          : 'fileScanResult.html',
                                reportName           : 'Security-Report',
                                reportTitles         : 'Security-Report'
                            ])
                            sh """
                                ls -la
                            """
                            def removeReport = httpRequest([
                                acceptType   : 'APPLICATION_JSON',
                                httpMode     : 'DELETE',
                                contentType  : 'APPLICATION_JSON',
                                customHeaders: [[name: "X-Auth", value: accunetix]],
                                url          : "${env.ACUNETIX_API_URL}/reports/$reportId",
                                ignoreSslErrors: true
                            ])
                        }

                    }
                }
            } catch (FlowInterruptedException interruptEx) {
                // check if exception is system timeout
                if (interruptEx.getCauses()[0] instanceof org.jenkinsci.plugins.workflow.steps.TimeoutStepExecution.ExceededTimeout) {
                    if (genReportRetry <= 10) {
                        genReportRetry += 1
                    } else {
                        error "Cannot get result from acunetix server. Build Failed."
                    }
                } else {
                    throw interruptEx
                }
            } finally {
                def testSecurityResultContent = "- Alert level high: <b>${severity_counts_high}</b> <br/>" +
                                    "- Alert level info: <b>${severity_counts_info}</b> <br/>" +
                                    "- Alert level low: <b>${severity_counts_low}</b> <br/>" +
                                    "- Alert level medium: <b>${severity_counts_medium}</b> <br/>"


                def testSecurityResultString = 	"<b> Security Scan Acunetix Result:</b> <br/><br/>${testSecurityResultContent} " +
                                    "<i><a href='${env.BUILD_URL}Security-Report/'>Details Scan Acunetix Report...</a></i><br/><br/>"
                env.SECURITY_RESULT_STR = testSecurityResultString
                println env.SECURITY_RESULT_STR
                if(severity_counts_info > 0 || severity_counts_low > 0 || severity_counts_medium > 0){
                    if(enableBypassSec == 'true'){
                        echo "Security scan alert found high, low or medium"
                    } else {
                        error "Security scan alert found high, low or medium"
                    }
                }
            }
        }

    }
}
def release2k8s(version){
    sh '''
        mkdir {Nhập tên folder cho source deployment}
        cd {Nhập tên folder cho source deployment}
    '''
    checkout changelog: true, poll: true, scm: [
        $class                           :  'GitSCM',
        branches                         : [[name: "master"]],
        doGenerateSubmoduleConfigurations: false,
        extensions                       : [[$class: 'UserIdentity',
                                            email : 'userGit@viettel.com.vn', name: 'userGit'],
                                            [$class: 'CleanBeforeCheckout']],
        submoduleCfg                     : [],
        userRemoteConfigs                : [[credentialsId: "credentialsId",
                                             url          : "Copy url Git Source Deployment" +".git"]]
    ]
    sleep(5)
    sh """
        pwd
        ls -la
        sh k8s/etc/update-version.sh ${version} --kubeconfig=[tên file config credential k8s]
        cd k8s/etc  --> phụ thuộc cấu trúc thư mục trong source deploy
        kubectl -n [nhập tên namespace] apply -f . --kubeconfig=[tên file config credential k8s]
    """

    sleep(180)
    sh 'kubectl -n etc get pods,svc -kubeconfig=testuser-k8s-config'
    def checkProcessRunning = sh(returnStdout: true, script: "kubectl -n etc --kubeconfig='${configFile}' get pods --sort-by=.status.startTime | grep '${service}' | tail -n 1 | awk '{print \$3}'").trim()
    echo "checkProcessRunning: $checkProcessRunning ${service}"

    if(checkProcessRunning == "Running") {
        env.STAGING_PORT = sh(returnStdout: true, script: "kubectl -n etc --kubeconfig='${configFile}' get svc | grep '${service}' | awk '{print \$5}' | grep -o '[[:digit:]]*' | tail -n 1").trim()
        echo "port: $env.STAGING_PORT"
        env.STAGING_IP = sh(returnStdout: true, script: "kubectl -n etc --kubeconfig='${configFile}' get node -o wide | head -2 | tail -1 | awk '{print \$6'}").trim()
        echo "ip: $env.STAGING_IP"
    } else {
        error "Deploy service ${service} version ${version} to k8s ${enviroment} failure open port $env.STAGING_PORT"
    }
}
// script example
def autoTest(ip, tags){
    if(env.automations_test == ""){
       echo "Skip automations Test"
    } else {
      sleep(60)
      node("slave_158.40"){
          try {
              checkout changelog: true, poll: true, scm: [
              $class                           :  'GitSCM',
              branches                         : [[name: "master"]],
              doGenerateSubmoduleConfigurations: false,
              extensions                       : [[$class: 'UserIdentity',
                                                  email : 'hienptt22@viettel.com.vn', name: 'hienptt22'],
                                                  [$class: 'CleanBeforeCheckout']],
              submoduleCfg                     : [],
              userRemoteConfigs                : [[credentialsId: "63265de3-8396-40f9-803e-5cd0b694e519",
                                                                  url          : "${env.automations_test}"]]
              ]
              bat """
                  mvn clean verify -U -Dfile.encoding=UTF-8 ${tags}
              """
          } catch (err) {
              echo "error: ${err}"
              throw err
          } finally {
              publishHTML([
              allowMissing         : false,
              alwaysLinkToLastBuild: false,
              keepAll              : true,
              reportDir            : 'target/site/serenity',
              reportFiles          : 'index.html',
              reportName           : 'Serenity HTML Report',
              reportTitles         : 'ETC-BatThuong'])
              def testResultComment = "<b>Functional Test Result:</b> <br/><br/>" +
                      "<i><a href='${env.BUILD_URL}HTML_20Report/'>Details Funcational Test Report...</a></i><br/><br/>"
              env.FUNCTIONAL_TEST_RESULT_STR = testResultComment
          }
      }
    }
}
def autoPerfomance(){
    if(env.jobAutoPerform == ""){
        echo "skip automations test"
    } else {
        sleep(30)
        build job: "${env.jobAutoPerform}"
    }
}
/*
    - Config các stage run when push commit
    - SonarQube
    - Build
    - Deploy
*/
def buildPushCommit() {

    echo "gitlabBranch: $env.gitlabBranch"
    def crmBackendPom = readMavenPom([file: "pom.xml"])
    def crmBackendVersion = crmBackendPom.getVersion()
    def version = "${crmBackendVersion}_${env.gitlabBranch}_u${BUILD_NUMBER}"
    echo " Version project : ${crmBackendVersion}_${env.gitlabBranch}_u${BUILD_NUMBER}"
    env.project_version=version
    def tasks = [:]
    tasks['SonarQubeScan'] = {
        node("$env.node_slave") {
            echo "test sonar"
            sonarQubeScan("PUSH")
        }
    }

    tasks['Package and Build Artifact'] = {
        node("$env.node_slave") {
            stage ('Build Package & Image Docker'){
                buildService("PUSH","${version}")
            }
        }
    }
    parallel tasks
    def uploads = [:]
    def deploys=[:]
    def tests = [:]
    if(env.gitlabBranch == env.STAGING_BRANCH){
        uploads['Push Image To Repo Nexus'] = {
            node("$env.node_slave"){
                dir("$env.buildFolderResult"){
                    stage('Packaging Module Web and Uploader To Nexus'){
                        packageServicesAndUploadToRepo("moduleName","CI_staging","artifactName")
                    }
                }
            }
        }
        uploads['Push Image To Repo Docker'] = {
            node("$env.node_slave"){
                stage('Push Image To Repo Docker'){
                    pushImageToDockerRepo("${version}")
                }
            }
        }
        parallel uploads

        deploys['Deploy to Server test'] = {
            node("$env.node_slave"){
               stage('Deploy to Server test'){
                    echo 'deploy server'
                    deploy_module_web("test","moduleName","CI_staging_Back-End")
                }
            }
        }
        deploys['Deploy to K8s'] = {
            node("$env.node_slave"){
               stage('Deploy to K8s'){
                    echo 'deploy to K8s'
                    release2k8s("${version}")
                }
            }
        }
        parallel deploys

        tests["Run Automations Test"] = {
            stage("Run Automations Test"){
                if(env.STAGING_PORT_FRONT_END == ""){
                    echo "Front-end don't running. Skip AutomationsTest"
                } else {
                    autoTest("${env.STAGING_IP}:${env.STAGING_PORT_FRONT_END}", "${env.tagsTest}")
                }
            }
        }
        tests["Run Security Test"] = {
            stage("Run Security Test"){
                acunetixScan("${env.STAGING_IP}:${env.STAGING_PORT}")
            }
        }
        parallel tests

        stage("Run Auto Performance Test"){
             if(env.STAGING_PORT_FRONT_END == ""){
                    echo "Front-end don't running. Skip Performance Job"
            } else {
                autoPerfomance()
            }
        }

    } else {
        uploads['Push Image To Repo Nexus'] = {
            node("$env.node_slave"){
                dir("$env.buildFolderResult"){
                    stage('Packaging Module Web and Uploader To Nexus'){
                        packageServicesAndUploadToRepo("moduleName","CI_staging","artifactName")
                    }
                }
            }
        }
        uploads['Push Image To Repo Docker'] = {
            node("$env.node_slave"){
                stage('Push Image To Repo Docker'){
                    pushImageToDockerRepo("${version}")
                }
            }
        }
        parallel uploads

        deploys['Deploy to Server test'] = {
            node("$env.node_slave"){
                stage('Deploy to Server test'){
                    echo 'deploy server'
                    deploy_module_web("test","ETC_CRM","CI_dev_Back-End")
                }
            }
        }
        deploys['Deploy to K8s'] = {
            node("$env.node_slave"){
               stage('Deploy to K8s'){
                    echo 'deploy to K8s'
                    release2k8s("${version}")
                }
            }
        }
        parallel deploys
    }

    currentBuild.result = "SUCCESS"

}
/*
  Sửa các stage cho phù hợp với dự án
*/
def buildMergeRequest() {
    echo "gitlabBranch: $env.gitlabTargetBranch"
    def crmBackendPom = readMavenPom([file: "pom.xml"])
    def crmBackendVersion = crmBackendPom.getVersion()
    def version = "${crmBackendVersion}_${env.gitlabTargetBranch}_u${BUILD_NUMBER}"
    echo " Version project : ${crmBackendVersion}_${env.gitlabTargetBranch}_u${BUILD_NUMBER}"
    env.project_version=version
    def tasks = [:]
    tasks['SonarQube Scan'] = {
        node("$env.node_slave") {
           echo "test sonar"
           sonarQubeScan("MERGE")
        }
    }
    tasks['Package and Build Artifact'] = {
        node("$env.node_slave") {
            stage ('Build'){
                buildService("MERGE","${version}")
            }
        }
    }
    parallel tasks
    if (env.gitlabTargetBranch == env.STAGING_BRANCH){
        stage('Packaging Module Web and Uploader'){
            dir("$env.buildFolderResult"){
                packageServicesAndUploadToRepo("moduleName","CI_staging","artifactName")
            }
        }

        stage('Deploy to server staging'){
            node("$env.node_slave"){
                echo 'deploy server'
                deploy_module_web("staging","moduleName","CI_staging_artifactName")
            }
        }
    } else {
        stage('Packaging Module Web and Uploader'){
            dir("$env.buildFolderResult"){
                packageServicesAndUploadToRepo("moduleName","CI_dev","artifactName")
            }
        }
        stage('Deploy to server test'){
            node("$env.node_slave"){
                echo 'deploy server'
                deploy_module_web("test","moduleName","CI_dev_artifactName")
            }
        }
    }
    currentBuild.result = "SUCCESS"
}


return [
    buildPushCommit      : this.&buildPushCommit,
    buildMergeRequest    : this.&buildMergeRequest,
    buildAcceptAndCloseMR: this.&buildAcceptAndCloseMR,
    sonarQubeScan        : this.&sonarQubeScan,
    buildService        : this.&buildService,
    deploy_module_web    : this.&deploy_module_web,
    packageServicesAndUploadToRepo: this.&packageServicesAndUploadToRepo

]
