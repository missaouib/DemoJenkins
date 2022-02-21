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

/*
    - Build all module.
    - change module to build in def buildService
*/
def buildService(buildType, buildTask) {
    stage("Checkout Source Code") {
        jenkinsfile_utils.checkoutSourceCode(buildType)
        echo  'Checkout source code'
    }
    stage('Build APK'){
       def folder = sh(script: 'pwd', returnStdout: true)
       env.buildFolderResult = folder.trim()
        dir("$env.buildFolderResult"){
           if (fileExists('local.properties')) {
            sh label: 'Replace sdk.dir', script:
            'sed -i \'s/sdk.dir.*/sdk.dir=sdk.dir=/home/app/server/android-sdks/\' local.properties'
            }
            sh '''
                sed -i \'s/jcenter()//\' build.gradle
                sed -i \'s/google()/maven{url "http:\\/\\/10.60.108.23:9001\\/repository\\/maven-public\\/"}/\' build.gradle
            '''
            sh label: 'Clean before build', script:
            '''/home/app/server/gradle-5.5/bin/gradle wrapper
            ./gradlew clean'''
            if(buildTask == 'TEST'){
                buildTask = 'assembleTESTDebug'
            } else{
                buildTask = 'assembleRELRelease'
            }
            sh "./gradlew app:" + buildTask
        }
    }
}
def releaseApp(buildTask,host){
    dir("$env.buildFolderResult"){
        def outputDir
    	def fileSearch
    	if(buildTask == 'TEST'){
    	    outputDir = 'app/build/outputs/apk/TEST/debug/'
    	    fileSearch = 'TEST'
    	} else{
    	    outputDir = 'app/build/outputs/apk/REL/release/'
    	    fileSearch = 'REL'
    	}
    	files = findFiles(glob: 'app/build/outputs/apk/**/*' + fileSearch + '*_all.apk')
        for(apk in files) {
            if(apk.name.contains('_all.apk')){
                sh label: 'Create jenkin_ansible_ci', script:
                '''touch jenkins_ansible_ci.yml
                echo \'\'\'
                - hosts: ''' + host +'''
                  remote_user: app
                  tasks:
                  - name: Copy file
                    copy:
                     src: "''' + outputDir + apk.name + '''"
                     dest: "/u01/data/dmsone/apk/''' + apk.name + '''"
                \'\'\' > jenkins_ansible_ci.yml'''
                ansiblePlaybook installation: 'ansible-2.0', playbook: 'jenkins_ansible_ci.yml'
                echo "http://" + params.host + ":8000/dmsone/apk/" + apk.name
                break
            }
        }
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
    def tasks = [:]
    tasks['SonarQubeScan'] = {
        node("$env.node_slave") {
            echo "test sonar"
            sonarQubeScan("PUSH")
        }
    }
    if(env.gitlabBranch == env.STAGING_BRANCH){
        echo " Start release"
        echo "STAGING_BRANCH: $env.STAGING_BRANCH"
        tasks['Package and Build Artifact'] = {
            node("$env.node_slave") {
                stage ('Build Package'){
                    buildService("PUSH","REL")
                }
            }
        }
        parallel tasks
        stage('Release APK'){
            releaseApp("REL","$env.hostStaging")
        }
    } else {
        tasks['Package and Build Artifact'] = {
            node("$env.node_slave") {
                stage ('Build Package'){
                    buildService("PUSH","TEST")
                }
            }
        }
        parallel tasks
        stage('Release APK'){
            releaseApp("TEST","$env.hostTest")
        }
    }
    currentBuild.result = "SUCCESS"

}
/*
  Sửa các stage cho phù hợp với dự án
*/
def buildMergeRequest() {
    echo "gitlabBranch: $env.gitlabTargetBranch"

    def tasks = [:]
    tasks['SonarQube Scan'] = {
        node("$env.node_slave") {
           echo "test sonar"
           sonarQubeScan("MERGE")
        }
    }
    if (env.gitlabTargetBranch == env.STAGING_BRANCH){
        tasks['Package and Build Artifact'] = {
            node("$env.node_slave") {
                stage ('Build Package'){
                    buildService("PUSH","RELEASE")
                }
            }
        }
        parallel tasks
        stage('Release APK'){
            releaseApp("RELEASE","$env.hostStaging")
        }
    } else {
        tasks['Package and Build Artifact'] = {
            node("$env.node_slave") {
                stage('Build Package'){
                    buildService("PUSH","TEST")
                }
            }
        }
        parallel tasks
        stage('Release APK'){
            releaseApp("TEST","$env.hostTest")
        }
    }
    currentBuild.result = "SUCCESS"
}


return [
    buildPushCommit      : this.&buildPushCommit,
    buildMergeRequest    : this.&buildMergeRequest,
    buildAcceptAndCloseMR: this.&buildAcceptAndCloseMR

]
