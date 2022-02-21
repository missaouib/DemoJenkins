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

def sonarQubeScanAndUnitTest(buildType) {
    stage("Checkout Source Code") {
        jenkinsfile_utils.checkoutSourceCode(buildType)
        echo  'Checkout source code'
        sh 'mvn clean'
    }
    stage('Unit Test & Code Coverage'){
        try {
            echo "code coverage started"
            sh 'mvn test org.jacoco:jacoco-maven-plugin:0.8.4:report-aggregate'
            echo "code coverage done"
            jacoco([
                classPattern: 'ms-device/target/classes,auth/target/classes,ms-user-manage/target/classes,ms-app-manage/target/classes', 
                sourcePattern: 'ms-device/src/main/java,,auth/src/main/java,ms-user-manage/src/main/java,ms-app-manage/src/main/java'
            ])
            def coverageResultStrComment = "<b>Coverage Test Result:</b> <br/><br/>"
            def coverageInfoXmlStr = readFile "cicd/target/jacoco-aggregate-report/jacoco.xml"
            echo "Coverage Info: ${getProjectCodeCoverageInfo(coverageInfoXmlStr)} "
            coverageResultStrComment += getProjectCodeCoverageInfo(coverageInfoXmlStr)
            coverageResultStrComment += "<i><a href='${env.BUILD_URL}Code-Coverage-Report/'>" +
                "Details Code Coverage Test Report...</a></i><br/><br/>"
            env.CODE_COVERAGE_RESULT_STR = coverageResultStrComment
        } catch (err) {
            echo "Error when test Unit Test"
            throw err
        } finally {
            sh 'ls -al'
            junit '*/target/*-results/test/TEST-*.xml'
            def unitTestResult = getTestResultFromJenkins()

            env.UNIT_TEST_PASSED = unitTestResult["passed"]
            env.UNIT_TEST_FAILED = unitTestResult["failed"]
            env.UNIT_TEST_SKIPPED = unitTestResult["skipped"]
            env.UNIT_TEST_TOTAL = unitTestResult["total"]

            def testResultContent = "- Passed: <b>${unitTestResult['passed']}</b> <br/>" +
                "- Failed: <b>${unitTestResult['failed']}</b> <br/>" +
                "- Skipped: <b>${unitTestResult['skipped']}</b> <br/>"

            def testResultString = "<b> Unit Test Result:</b> <br/><br/>${testResultContent} " +
                "<i><a href='${env.BUILD_URL}testReport/'>Details Unit Test Report...</a></i><br/><br/>"
            env.UNIT_TEST_RESULT_STR = testResultString

            if (unitTestResult['failed'] > 0) {
                error "Failed ${unitTestResult['failed']} unit tests"
            }
        }
    }
    stage('SonarQube analysis') {
        env.SONAR_QUBE_PROJECT_KEY = genSonarQubeProjectKey()
        withSonarQubeEnv('SONARQ_V6'){
                sh "/home/app/server/sonar-scanner/bin/sonar-scanner " +
                    "-Dsonar.projectName=${env.SONAR_QUBE_PROJECT_KEY} " +
                    "-Dsonar.projectKey=${env.SONAR_QUBE_PROJECT_KEY} " +
                    "-Dsonar.java.binaries=. " +
                    "-Dsonar.sources=./ " +
                    "-Dsonar.exclusions=**/res/**,**/target/**,**/build/**,**/share/**,**.html"
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
                                echo "Sonar contain error"
                                // error "Pipeline failed due to quality gate failure: ${qg.status}"
                            }
                        }
                    }
                } catch (FlowInterruptedException interruptEx) {
                    // check if exception is system timeout
                    if (interruptEx.getCauses()[0] instanceof org.jenkinsci.plugins.workflow.steps.TimeoutStepExecution.ExceededTimeout) {
                        if (sonarQubeRetry <= 10) {
                            sonarQubeRetry += 1
                        } else {
                            echo "Sonar contain error"
                            // error "Cannot get result from Sonarqube server. Build Failed."
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
    stage("Build Auth") {
        buildModuleService("auth","t-monitor-auth-0.0.1-SNAPSHOT.jar","thinghub_auth")
    }
    stage("Build Service discovery") {
        buildModuleService("service-discovery","viettel-discovery-5.0.1.jar","thinghub_sv_discovery")
    }
    stage("Build User Manage") {
        buildModuleService("ms-user-manage","msusermanage-0.0.1-SNAPSHOT.jar","thinghub_usermanage")
    }
    stage("Build App Manage") {
        buildModuleService("ms-app-manage","msappmanage-0.0.1-SNAPSHOT.jar","thinghub_appmanage")
    }
    stage("Build Device Manage") {
        buildModuleService("ms-device","msdevicemanage-0.0.1-SNAPSHOT.jar","thinghub_devicemanage")
    }
    stage("Build Gateway") {
        buildModuleServiceGateway("gateway","gateway-0.0.1-SNAPSHOT.jar","thinghub_gateway")
    }

}

def buildModuleService(folder, jar, name){
    dir("/u01/jenkins/workspace/thinghub/CI/${folder}"){
            sh """
               mvn clean install -DskipTests=true
               cp target/${jar} docker/
               cd docker
               ls -la
               docker build . -t 10.60.156.72/thinghubs/${name}:${BUILD_NUMBER}
               docker tag 10.60.156.72/thinghubs/${name}:${BUILD_NUMBER} 10.60.156.72/thinghubs/${name}:latest
               docker push 10.60.156.72/thinghubs/${name}:${BUILD_NUMBER}
               docker push 10.60.156.72/thinghubs/${name}:latest
               docker rmi -f 10.60.156.72/thinghubs/${name}:${BUILD_NUMBER}
               """
        }
}

def buildModuleServiceGateway(folder, jar, name){
    dir("/u01/jenkins/workspace/thinghub/CI/${folder}"){
            sh(returnStatus: true, script: 'mvn clean install -DskipTests=true')
            sh(returnStatus: true, script: '''chmod +x rebuild.sh
                bash rebuild.sh
                sed -n 490,500p node_modules/bootstrap/scss/_variables.scss
                mvn clean install -DskipTests=true''')
            sh """
                mvn clean install -DskipTests=true
                cp target/${jar} docker/
                cd docker
                ls -la
                docker build . -t 10.60.156.72/thinghubs/${name}:${BUILD_NUMBER}
                docker tag 10.60.156.72/thinghubs/${name}:${BUILD_NUMBER} 10.60.156.72/thinghubs/${name}:latest
                docker push 10.60.156.72/thinghubs/${name}:${BUILD_NUMBER}
                docker push 10.60.156.72/thinghubs/${name}:latest
                docker rmi -f 10.60.156.72/thinghubs/${name}:${BUILD_NUMBER}
                """
        }
}

def release2k8s(){
    dir('/u01/jenkins/workspace/thinghub/CI/'){
         sh '''
             mkdir thinghub-release
             cd thinghub-release
             '''
        checkout changelog: true, poll: true, scm: [
            $class                           :  'GitSCM',
            branches                         : [[name: "master"]],
            doGenerateSubmoduleConfigurations: false,
            extensions                       : [[$class: 'UserIdentity',
                                                 email : 'hienptt22@viettel.com.vn', name: 'hienptt22'],
                                                [$class: 'CleanBeforeCheckout']],
            submoduleCfg                     : [],
            userRemoteConfigs                : [[credentialsId: "63265de3-8396-40f9-803e-5cd0b694e519",
                                                 url          : "http://10.60.156.11/truongdxx/thinghub-release" +".git"]]
        ]
        sh '''
            ls -la
            sudo cat ~root/.kube/config
            sudo rm -f ~root/.kube/config
            sudo cp config ~root/.kube/
            sudo kubectl get nodes
            sudo kubectl delete -f discovery.yaml,auth.yaml,usermanage.yaml,appmanage.yaml,devicemanage.yaml,gateway.yaml
            '''
        sleep(5)
        sh '''
            sudo kubectl apply -f discovery.yaml
        '''
        sleep(5)
        sh '''
            sudo kubectl apply -f auth.yaml,usermanage.yaml,appmanage.yaml,devicemanage.yaml
        '''
        sleep(5)
        sh '''
            sudo kubectl apply -f gateway.yaml
        '''
        sleep(5)
        sh '''
            sudo kubectl get pods,svc
        '''
    }
}
/*
    - Zip artifact build and upload to Nexus Repository
    - Config module with moduleName
    - 'check' to identify path to zip file
*/
/*
    - Config các stage run when push commit
    - SonarQube
    - Build
    - Deploy
*/
def buildPushCommit() {
    def tasks = [:]
    tasks['sonarQubeScanAndUnitTest'] = {
        node("slave_43") {
           echo "test sonar"
           sonarQubeScanAndUnitTest("PUSH")
           
        }
    }
    
    tasks['Package and Build Artifact'] = {
        node("slave_43") { 
            stage ('Build'){
                buildSerrvice("PUSH")
            }
        }
    }
    parallel tasks
    stage('Deploy to k8s'){
        node("slave_43") {
            release2k8s()
        }
    }
    stage('Get services'){
        node("slave_43") {
            sh 'sudo kubectl get svc'
        }
    }
    
    currentBuild.result = "SUCCESS"
}

def buildMergeRequest() {
    currentBuild.result = "SUCCESS"
}


return [
    buildPushCommit                 : this.&buildPushCommit,
    buildMergeRequest               : this.&buildMergeRequest,
    buildAcceptAndCloseMR           : this.&buildAcceptAndCloseMR,
    sonarQubeScanAndUnitTest        : this.&sonarQubeScanAndUnitTest,
    buildSerrvice                   : this.&buildSerrvice,
    deploy_module_web               : this.&deploy_module_web,
    packageServicesAndUploadToRepo  : this.&packageServicesAndUploadToRepo
]
