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
def getlistServiceRebuild(buildType){
    def listServiceRebuild = []
    def listServiceChange = []
    def specialList=["Campaign","CampaignProcess"]
    listServiceChange = jenkinsfile_utils.getChangSet()
    if(listServiceChange.size == 0){
        for(def ii=0; ii<specialList.size(); ii++){
            if(jenkinsfile_utils.hasChangesIn(env.gitlabBranch,buildType,"${specialList[ii]}")){
                listServiceRebuild.add("${specialList[ii]}")
            }else {
                echo "No module change"
            }
        }
    } else {
        for(def i = 0; i < listServiceChange.size(); i++){
            println("listServiceRebuild $i:$listServiceChange[i]")
            for(def ij=0;ij<specialList.size();ij++){
                if(listServiceChange[i].contains("${specialList[ij]}/")){
                    if(!listServiceRebuild.contains("${specialList[ij]}")){
                        listServiceRebuild.add("${specialList[ij]}")
                        println("listServiceRebuild: $listServiceRebuild")
                        echo "exit ${specialList[ij]}"
                    } else {
                    echo "service ${specialList[ij]} added"
                    }
                }else {
                    echo "No service change in code"
                }
            }
        }
    }
    println "listServiceRebuild: $listServiceRebuild"
    for(def i = 0; i < listServiceRebuild.size(); i++){
        echo "listRebuild: ${listServiceRebuild[i]}"
    }
    return listServiceRebuild
}
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
                "-Dsonar.sources=./CampaignProcess,./Campaign " +
                "-Dsonar.exclusions=**/target/**,**/Libs/**,**/dist/**,**/pmd-results/**,**/nbproject/**"
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
def buildService(buildType, moduleName) {
    stage("Build $moduleName"){
       dir("$env.buildFolderResult"){
            withAnt(installation: 'apache_ant1.9', jdk: 'java_1.7') {
                echo "$ANT_HOME"
                echo "$JAVA_HOME"
                sh """
                    pwd
                    cd $moduleName
                    ant -file build.xml
                """

            }
       }
    }
}
/*
    - Config module with moduleName
    - 'check' to identify path to zip file
*/
def packageServicesAndUploadToRepo(groupId, artifactId, moduleName, type){
    sh """
        echo "start package file"
        pwd
    """
    def uploadSuccessComment = "<b>Build & package Artifact Results - " +
                               "Build Artifact module ${moduleName} is created. "
    nexusArtifactUploader artifacts: [[artifactId: "${artifactId}_${moduleName}", classifier: '', file: "$moduleName/dist/$moduleName.$type", type: "$type"]], credentialsId: "$env.NEXUS_CREDENTIALSID", groupId: "${groupId}", nexusUrl: '10.60.156.26:8081', nexusVersion: 'nexus3', protocol: 'http', repository: 'msbuild', version: "1.${BUILD_NUMBER}"
    env.PACKAGE_UPLOAD_IMAGE_RESULT_STR = uploadSuccessComment
}

def deploy_module_web(server,groupId,artifactId,type){
    echo "deploy to server ${server}"
    sh """
        pwd
        ansible-playbook cicd/deploy/deploy_${server}.yml -e groupId=${groupId} -e artifactId=${artifactId} -e BUILD_NUMBER=${BUILD_NUMBER} -e TYPE=${type} -vvv
    """
    // ansible-playbook cicd/deploy/deploy_file_${server}.yml -e groupId=${groupId} -e artifactId=${artifactId} -e BUILD_NUMBER=${BUILD_NUMBER} -e TYPE=${type}
}
/*
    - Config các stage run when push commit
    - SonarQube
    - Build
    - Deploy
*/
def buildPushCommit() {
    echo "gitlabBranch: $env.gitlabBranch"
    def tasks = [:]
    tasks['SonarQubeScan'] = {
        node("$env.node_slave") {
            echo "test sonar"
            sonarQubeScan("PUSH")
        }
    }
    def listRebuild = getlistServiceRebuild("PUSH")
    println "Servce build: $listRebuild"
    if(listRebuild.size == 0){
        println("No Module Rebuild")
    } else {
        stage("Checkout Source Code") {
            jenkinsfile_utils.checkoutSourceCode("PUSH")
            echo  'Checkout source code'
            def folder = sh(script: 'pwd', returnStdout: true)
            env.buildFolderResult = folder.trim()
            echo "$env.buildFolderResult"
        }
        dir("$env.buildFolderResult"){
            for( def i = 0; i< listRebuild.size(); i++){
                def service = listRebuild[i]
                tasks["$service"] = {
                    echo "$service"
                    buildService("PUSH","${service}")
                    if(service == "Campaign"){
                        stage("Package & Upload to Nexus"){
                            packageServicesAndUploadToRepo("IPCCV1","CI_dev","$service",'war')
                        }
                        stage("Deploy to server test"){
                            deploy_module_web("${service}","IPCCV1","CI_dev_${service}",'war')
                        }
                    } else if (service == "CampaignProcess"){
                        stage("Package & Upload to Nexus"){
                            packageServicesAndUploadToRepo("IPCCV1","CI_dev","$service",'jar')
                        }
                        stage("Deploy to server test"){
                            deploy_module_web("${service}","IPCCV1","CI_dev_${service}",'jar')
                        }
                    }
                }

            }
        }
    }
     parallel tasks
    currentBuild.result = "SUCCESS"

}
/*
  Sửa các stage cho phù hợp với dự án
*/
def buildMergeRequest() {
def tasks = [:]
    tasks['SonarQubeScan'] = {
        node("$env.node_slave") {
            echo "test sonar"
            sonarQubeScan("MERGE")
        }
    }
    def listRebuild = getlistServiceRebuild("MERGE")
    println "Servce build: $listRebuild"
    if(listRebuild.size == 0){
        println("No Module Rebuild")
    } else {
        stage("Checkout Source Code") {
            jenkinsfile_utils.checkoutSourceCode("MERGE")
            echo  'Checkout source code'
            def folder = sh(script: 'pwd', returnStdout: true)
            env.buildFolderResult = folder.trim()
            echo "$env.buildFolderResult"
        }
        dir("$env.buildFolderResult"){
            for( def i = 0; i< listRebuild.size(); i++){
                def service = listRebuild[i]
                tasks["$service"] = {
                    echo "$service"
                    buildService("MERGE","${service}")
                    if(service == "Campaign"){
                        stage("Package & Upload to Nexus"){
                            packageServicesAndUploadToRepo("IPCCV1","CI_dev","$service",'war')
                        }
                        stage("Deploy to server test"){
                            deploy_module_web("${service}","IPCCV1","CI_dev_${service}",'war')
                        }
                    } else if (service == "CampaignProcess"){
                        stage("Package & Upload to Nexus"){
                            packageServicesAndUploadToRepo("IPCCV1","CI_dev","$service",'jar')
                        }
                        stage("Deploy to server test"){
                            deploy_module_web("${service}","IPCCV1","CI_dev_${service}",'jar')
                        }
                    }
                }

            }
        }
    }
    parallel tasks
    currentBuild.result = "SUCCESS"
}


return [
    buildPushCommit      : this.&buildPushCommit,
    buildMergeRequest    : this.&buildMergeRequest,
    buildAcceptAndCloseMR: this.&buildAcceptAndCloseMR

]
