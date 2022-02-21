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
                "description": "http://${url}",
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
                "profile_id":"$env.modeScan",
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
            } finally{

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
// config run automations test serenity
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
                  mvn clean verify -Dwebdriver.base.url=http://${ip} -Ddatabase.IP=${env.STAGING_DB}
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
              reportTitles         : 'ETC-DMDC'])
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
    - Build all module.
    - change module to build in def buildService
*/
def buildService(buildType,version,service) {
    stage("Checkout Source Code") {
        jenkinsfile_utils.checkoutSourceCode(buildType)
        echo  'Checkout source code'
    }
    stage('Build module front end'){
      try {
        def folder = sh(script: 'pwd', returnStdout: true)
        env.buildFolderResult = folder.trim()
        sh 'npm install'

        echo "build module front-end"
        sh """
            npm run build:k8s
            ls -la
            echo "Start build docker"
            docker build . -t 10.60.156.72/etc/${service}:${version}
          """
      } catch( err){
        error "Build Failure"
      }
    }
}
def pushImageToDockerRepo(service,version){
    stage ("Login to Docker Repository"){
        withCredentials([usernamePassword(credentialsId: "$env.HABOR_CREDENTIALID", usernameVariable: 'username',
                passwordVariable: 'password')]){
            sh """
                docker --config ~/.docker/.[Nhập tên user đăng nhập habor] login -u ${username} -p '${password}' 10.60.156.72
            """
        }
    }
    stage ("Push image to Docker Repository"){
      sh """
        echo "Push ${service}"
        docker --config ~/.docker/.[Nhập tên user đăng nhập habor] push 10.60.156.72/etc/${service}:${version}
        docker rmi 10.60.156.72/etc/${service}:${version}
        echo "Finish Push ${service}"
      """
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
    echo "deploy to server ${server}"
    sh """
        pwd
        ansible-playbook cicd/deploy/deploy_etc_dmdc-webapp_ui_${server}.yml -e http_app_port=${http_app_port} -e groupId=${groupId} -e artifactId=${artifactId} -e BUILD_NUMBER=${BUILD_NUMBER}
    """
}
// Function deploy service with k8s
def release2k8s(version,enviroment,service,configFile){
    sh """
        if [ -d "${service}-release" ]; then
            echo "file exits";
        else mkdir ${service}-release && echo "create folder"
        fi;
        cd ${service}-release
    """
    checkout changelog: true, poll: true, scm: [
        $class                           :  'GitSCM',
        branches                         : [[name: "master"]],
        doGenerateSubmoduleConfigurations: false,
        extensions                       : [[$class: 'UserIdentity',
                                            email : 'userGit@viettel.com.vn', name: 'userGit'],
                                            [$class: 'CleanBeforeCheckout']],
        submoduleCfg                     : [],
        userRemoteConfigs                : [[credentialsId: ""$env.GITLAB_CREDENTIALID"",
                                             url          : "[Nhập đường dẫn source deployment]" +".git"]]
    ]
    sleep(5)
    def folderDeploy= sh(script: 'pwd', returnStdout: true)
    env.buildFolderDeployResult = folderDeploy.trim()
    try {
        sh """
            pwd
            cd k8s/${enviroment}
            ls -la
            sh update-version.sh ${version} ${service}-deployment
            kubectl -n etc apply -f ${service}-deployment* --kubeconfig=${configFile}
            kubectl -n etc apply -f ${service}-service* --kubeconfig=${configFile}
            kubectl -n etc apply -f etc-configmap.yml --kubeconfig=${configFile}
            sleep 120
        """
        dir("${env.buildFolderDeployResult}/k8s/${enviroment}"){
            echo "Get Pods, service detail"
            sh """
            kubectl -n etc get pods,svc --kubeconfig=${configFile}
            """
            def checkProcessRunning = sh(returnStdout: true, script: "kubectl -n etc --kubeconfig='${configFile}' get pods --sort-by=.status.startTime | grep '${service}' | tail -n 1 | awk '{print \$3}'").trim()
            echo "checkProcessRunning: $checkProcessRunning ${service}"
            def checkProcessBackendRunning = sh(returnStdout: true, script: "kubectl -n etc --kubeconfig='${configFile}' get pods --sort-by=.status.startTime | grep 'dmdc-backend' | tail -n 1 | awk '{print \$3}'").trim()
            echo "checkProcessBackendRunning: $checkProcessBackendRunning dmdc-backend "
            env.processBackendRunning=checkProcessBackendRunning
            if(checkProcessRunning == "Running") {
                env.STAGING_PORT = sh(returnStdout: true, script: "kubectl -n etc --kubeconfig='${configFile}' get svc | grep '${service}' | awk '{print \$5}' | grep -o '[[:digit:]]*' | tail -n 1").trim()
                echo "port: $env.STAGING_PORT"
                env.STAGING_IP = sh(returnStdout: true, script: "kubectl -n etc --kubeconfig='${configFile}' get node -o wide | head -2 | tail -1 | awk '{print \$6'}").trim()
                echo "ip: $env.STAGING_IP"
            } else {
                error "Deploy service ${service} version ${version} to k8s ${enviroment} failure open port $env.STAGING_PORT"
            }
        }
    }catch(err){
        error "Deploy to k8s failure"
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
    def dmdcFrontend = readJSON file: 'package.json'
    def dmdcFrontendVersion = dmdcFrontend.version
    def version = "${dmdcFrontendVersion}_${env.gitlabBranch}_u${BUILD_NUMBER}"
    def tasks = [:]
    tasks['SonarQubeScan'] = {
        node("$env.node_slave") {
            sonarQubeScan("PUSH")
        }
    }
    tasks['Package and Build Artifact'] = {
        node("$env.node_slave") {
            buildService("PUSH","${version}","dmdc-frontend")
        }
    }
    parallel tasks
    def uploads = [:]
    def deploys=[:]
    if(env.gitlabBranch == env.STAGING_BRANCH){
        uploads['Push Image To Repo Docker'] = {
            node("$env.node_slave"){
                stage('Push Image To Repo Docker'){
                    pushImageToDockerRepo("dmdc-frontend","${version}")
                }
            }
        }
        parallel uploads
        deploys['Deploy to K8s'] = {
            node("$env.node_slave"){
               stage('Deploy to K8s'){
                    echo 'deploy to K8s'
                    release2k8s("${version}","etc-staging","dmdc-frontend","config_108_172")
                }
            }
        }
        parallel deploys
        def tests = [:]
        if(env.processBackendRunning == 'Running'){
          tests["Run Automations Test"] = {
              stage("Run Automations Test"){
                autoTest("${env.STAGING_IP}:${env.STAGING_PORT}", "${env.tagsTest}")
              }
          }
          tests["Run Security Test"] = {
              stage("Run Security Test"){
                  acunetixScan("${env.STAGING_IP}:${env.STAGING_PORT}")
              }
          }
          parallel tests
          stage("Run Auto Performance Test"){
            autoPerfomance()
          }
        } else {
          echo "Skip due to backend not running"
        }

    } else {
        uploads['Push Image To Repo Docker'] = {
            node("$env.node_slave"){
                stage('Push Image To Repo Docker'){
                    pushImageToDockerRepo("dmdc-frontend","${version}")
                }
            }
        }
        parallel uploads
         deploys['Deploy to K8s TestLab'] = {
            node("$env.node_slave"){
               stage('Deploy to K8s'){
                    echo 'deploy to K8s'
                    release2k8s("${version}","etc","dmdc-frontend","config_155_31")
                }
            }
        }
        parallel deploys
        def tests = [:]
        if(env.processBackendRunning == 'Running'){
          if(env.enableAutoTest == 'true'){
            tests["Run Automations Test"] = {
              stage("Run Automations Test"){
                  autoTest("${env.STAGING_IP}:${env.STAGING_PORT}", "${env.tagsTest}")
                }
            }
          }
          tests["Run Security Test"] = {
              stage("Run Security Test"){
                  acunetixScan("${env.STAGING_IP}:${env.STAGING_PORT}")
              }
          }
          parallel tests
        } else {
          echo "Skip due to backend not running"
        }
    }

    currentBuild.result = "SUCCESS"
}

def buildMergeRequest() {
    echo "gitlabBranch: $env.gitlabBranch"
    echo "gitlabBranch: $env.gitlabTargetBranch"
    def dmdcFrontend = readJSON file: 'package.json'
    def dmdcFrontendVersion = dmdcFrontend.version
    def version = "${dmdcFrontendVersion}_${env.gitlabTargetBranch}_u${BUILD_NUMBER}"
    def tasks = [:]
    tasks['SonarQube Scan'] = {
        node("$env.node_slave") {
           sonarQubeScan("MERGE")
        }
    }
    tasks['Package and Build Artifact'] = {
        node("$env.node_slave") {
            buildService("MERGE","${version}","dmdc-frontend")
        }
    }
    parallel tasks
    def uploads = [:]
    def deploys=[:]
    def tests = [:]
    if(env.gitlabTargetBranch == env.STAGING_BRANCH){
        uploads['Push Image To Repo Docker'] = {
            node("$env.node_slave"){
                stage('Push Image To Repo Docker'){
                    pushImageToDockerRepo("dmdc-frontend","${version}","dmdc-frontend")
                }
            }
        }
        parallel uploads
        deploys['Deploy to K8s'] = {
            node("$env.node_slave"){
               stage('Deploy to K8s'){
                    echo 'deploy to K8s'
                    release2k8s("${version}","etc-staging","dmdc-frontend","config_108_172")
                }
            }
        }
        parallel deploys
        if(env.processBackendRunning == 'Running'){
          tests["Run Automations Test"] = {
              stage("Run Automations Test"){
                autoTest("${env.STAGING_IP}:${env.STAGING_PORT}", "${env.tagsTest}")
              }
          }
          tests["Run Security Test"] = {
              stage("Run Security Test"){
                  acunetixScan("${env.STAGING_IP}:${env.STAGING_PORT}")
              }
          }
          parallel tests
          stage("Run Auto Performance Test"){
            autoPerfomance()
          }
        } else {
          echo "Skip due to backend not running"
        }
    } else {
        uploads['Push Image To Repo Docker'] = {
            node("$env.node_slave"){
                stage('Push Image To Repo Docker'){
                    pushImageToDockerRepo("dmdc-frontend","${version}")
                }
            }
        }
        parallel uploads
         deploys['Deploy to K8s TestLab'] = {
            node("$env.node_slave"){
               stage('Deploy to K8s'){
                    echo 'deploy to K8s'
                    release2k8s("${version}","etc","dmdc-frontend","config_155_31")
                }
            }
        }
        parallel deploys
        if(env.processBackendRunning == 'Running'){
          if(env.enableAutoTest == 'true'){
            tests["Run Automations Test"] = {
              stage("Run Automations Test"){
                  autoTest("${env.STAGING_IP}:${env.STAGING_PORT}", "${env.tagsTest}")
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
            autoPerfomance()
          }
        } else {
          echo "Skip due to backend not running"
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
    packageServicesAndUploadToRepo: this.&packageServicesAndUploadToRepo,
    release2k8s          : this.&release2k8s

]
