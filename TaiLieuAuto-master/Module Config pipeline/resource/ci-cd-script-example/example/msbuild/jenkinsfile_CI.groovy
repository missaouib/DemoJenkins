import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import java.text.DecimalFormat
import hudson.tasks.test.AbstractTestResultAction
import hudson.plugins.cobertura.targets.CoverageMetric;
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
def getCoverageResultFromJenkins(){
    def coverageResult = [:]
    def coverageAction = currentBuild.rawBuild.getAction(hudson.plugins.cobertura.CoberturaBuildAction.class).getResults();
    coverageResult['LINE'] = coverageAction[CoverageMetric.LINE]
    coverageResult['LINE_PERCENTAGE'] = coverageAction[CoverageMetric.LINE].getPercentageFloat()
    coverageResult['PACKAGES']=coverageAction[CoverageMetric.PACKAGES]
    coverageResult['PACKAGES_PERCENTAGE']=coverageAction[CoverageMetric.PACKAGES].getPercentageFloat()
    coverageResult['CLASSES'] = coverageAction[CoverageMetric.CLASSES]
    coverageResult['CLASSES_PERCENTAGE'] = coverageAction[CoverageMetric.CLASSES].getPercentageFloat()
    coverageResult['METHOD']=coverageAction[CoverageMetric.METHOD]
    coverageResult['METHOD_PERCENTAGE']=coverageAction[CoverageMetric.METHOD].getPercentageFloat()
    coverageResult['CONDITIONAL']=coverageAction[CoverageMetric.CONDITIONAL]
    coverageResult['CONDITIONAL_PERCENTAGE']=coverageAction[CoverageMetric.CONDITIONAL].getPercentageFloat()
    return coverageResult
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


/*
  - functions cấu hình quét source code
    Khi cần thay đổi thường thay đổi các value
      sonar.sources --> cấu hình folder sẽ quét sonar
      sonar.exclusions  --> cấu hình các file không quét
*/
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
                "-Dsonar.sources=./ " +
                "-Dsonar.exclusions=**/bin/**,**/obj/**,**/App_Data/**,**/App_GlobalResources/**,**/packages/**,**/Dll/**,**/SMASRest.Test/**,**/Content/**,**/Scripts/**,**/SMAS.API.Logs/****/Template/**"
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
    // - stage lấy kết quả quét sonar
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
    - Chạy unit test và tính coverage
*/
def unitTestAndCodeCoverage(buildType){
    node("slave_203"){
        stage("Checkout source code"){
            jenkinsfile_utils.checkoutSourceCode(buildType)
        }
        stage("Unit Test & Code Coverage"){
            //Run lệnh test cho phù hợp. Sử dụng pipeline syntax để sinh lệnh
            bat "D:\\VS2017\\MSBuild\\15.0\\Bin\\MSBuild.exe " +
            "SParent.sln " +
            "/T:Clean;Build /p:Configuration=Release /p:AutoParameterizationWebConfigConnectionStrings=False /p:DeployOnBuild=true"
            bat """
                coverlet SParent.Business.Tests/bin/Release/SParent.Business.Tests.dll --target "dotnet" --targetargs "test -c Release --no-build --logger:"trx;LogFileName=TestResults.trx"" -f cobertura
                echo %errorlevel%
                """
            echo "code coverage done"
            cobertura autoUpdateHealth: false,
                      autoUpdateStability: false,
                      coberturaReportFile: 'coverage.cobertura.xml',
                      conditionalCoverageTargets: '70, 0, 0',
                      failUnhealthy: false, failUnstable: false,
                      lineCoverageTargets: '80, 0, 0', maxNumberOfBuilds: 0,
                      methodCoverageTargets: '80, 0, 0', onlyStable: false,
                      sourceEncoding: 'ASCII', zoomCoverageChart: false
            // publish result test and gen message result test
            mstest testResultsFile:"**/TestResults.trx", failOnError: true, keepLongStdio: true
            def unitTestResult = getTestResultFromJenkins()
            env.UNIT_TEST_PASSED = unitTestResult["passed"]
            env.UNIT_TEST_FAILED = unitTestResult["failed"]
            env.UNIT_TEST_SKIPPED = unitTestResult["skipped"]
            env.UNIT_TEST_TOTAL = unitTestResult["total"]
            def testResultContent = "- Passed: <b>${unitTestResult['passed']}</b> <br/>" +
                                    "- Failed: <b>${unitTestResult['failed']}</b> <br/>" +
                                    "- Skipped: <b>${unitTestResult['skipped']}</b> <br/>"
            def testResultString = 	"<b> Unit Test Result:</b> <br/><br/>${testResultContent} " +
                                    "<i><a href='${env.BUILD_URL}testReport/'>Details Unit Test Report...</a></i><br/><br/>"
            env.UNIT_TEST_RESULT_STR = testResultString

            def coverageCodeResult = getCoverageResultFromJenkins()
            def coverageInfoStr = "- <b>LINE: </b>: ${coverageCodeResult['LINE']} (<b>${coverageCodeResult['LINE_PERCENTAGE']}%</b>)<br/>" +
                                  "- <b>PACKAGES: </b>: ${coverageCodeResult['PACKAGES']} (<b>${coverageCodeResult['PACKAGES_PERCENTAGE']}%</b>)<br/>" +
                                  "- <b>CLASSES: </b>: ${coverageCodeResult['CLASSES']} (<b>${coverageCodeResult['CLASSES_PERCENTAGE']}%</b>)<br/>" +
                                  "- <b>METHOD: </b>: ${coverageCodeResult['METHOD']} (<b>${coverageCodeResult['METHOD_PERCENTAGE']}%</b>)<br/>" +
                                  "- <b>CONDITIONAL: </b>: ${coverageCodeResult['CONDITIONAL']} (<b>${coverageCodeResult['CONDITIONAL_PERCENTAGE']}%</b>)<br/>"

            echo coverageInfoStr
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
    stage('Build all module'){
        bat "D:\\VS2017\\MSBuild\\15.0\\Bin\\MSBuild.exe " +
                "SParent.sln " +
                "/T:Clean;Build /p:Configuration=Release /p:AutoParameterizationWebConfigConnectionStrings=False /p:DeployOnBuild=true"
    }
}
/*
    - Thực hiện xóa file dll kết nối DB và file Web.config
    - Zip artifact build and upload to Nexus Repository
    - Config module with moduleName
    - 'check' to identify path to zip file
*/
def packageServicesAndUploadToRepo(groupId, artifactId, moduleName){
    stage("Remove file DB dll and file config"){
        echo "Remove file DB dell and file config"
        bat """
           del ".\\${moduleName}\\obj\\Release\\Package\\PackageTmp\\bin\\Oracle.DataAccess.dll" ".\\${moduleName}\\obj\\Release\\Package\\PackageTmp\\Web.config"
        """

    }
    stage("Packaging module ${moduleName}"){
        echo "Packaging zip file"
         bat "7z a -tzip " +
                "${groupId}_${artifactId}_${moduleName}.zip " +
                ".\\${moduleName}\\obj\\Release\\Package\\PackageTmp\\*"
    }
    stage("Upload artifact module ${moduleName}to Nexus server"){
        def uploadSuccessComment = "<b>Build & package Artifact Results - " +
                                      "Build Artifact module ${moduleName} is created. "
            nexusArtifactUploader artifacts: [[artifactId: "${artifactId}_${moduleName}", classifier: '', file: "${groupId}_${artifactId}_${moduleName}.zip", type: 'zip']], credentialsId: "$env.NEXUS_CREDENTIALSID", groupId: "${groupId}", nexusUrl: '10.60.156.26:8081', nexusVersion: 'nexus3', protocol: 'http', repository: 'msbuild', version: "1.${BUILD_NUMBER}"
            env.PACKAGE_UPLOAD_IMAGE_RESULT_STR = uploadSuccessComment
    }
}
/*
   - Hàm thực hiện deploy ứng dụng tới server.
   - server: biến định nghĩa triển khai tới server test hay staging
   - options -e trong câu lệnh ansible được sử dụng để chỉ định các biến trong file yml.
*/
def deploy_module_web(server, groupId, artifactId){
    echo "deploy to server ${server}"
    sh " sudo ansible-playbook deploy_smas_slldt_${server}.yml -e VERSION=${BUILD_NUMBER} -e GROUP_ID=${groupId} -e ARTIFACT_ID=${artifactId}"
}
/*
    - Lưu ý, đọc kỹ trước khi sửa đổi phần này hoặc liên hệ với P.CNSX để hỗ trợ thay đổi.
    - Config các stage run when push commit
    - buildPushCommit: Hàm cấu hình các stage chạy khi có sự kiện push commit.
    - buildMergeRequest: Hàm cấu hình các stage chạy khi có sự kiện push commit.
    - Theo cách thức đang triển khai phát triển của dự án, hàm thực hiện các công việc sau:
       push commit tới nhánh feature: quét sonar, build, deploy tới server test.
       push or merge tới nhánh master: quét sonar, build, deploy tới server staging.
*/
def buildPushCommit() {

    echo "gitlabBranch: $env.gitlabBranch"

    def tasks = [:]
    tasks['unitTestAndCodeCoverage'] = {
        node("slave_43") {
            echo "Unit Test"
            unitTestAndCodeCoverage("PUSH")
        }
    }
    tasks['SonarQubeScan'] = {
        node("slave_43") {
            echo "test sonar"
            sonarQubeScan("PUSH")
        }
    }

    tasks['Package and Build Artifact'] = {
        node("slave_203") {
            stage ('Build'){
                buildService("PUSH")
            }
            if(env.gitlabBranch == env.STAGING_BRANCH) {
                echo "package to upload to server staging"
                 stage('Packaging Module Web and Uploader'){
                   packageServicesAndUploadToRepo("SMAS_SLLDT","CI_Staging","SParentWebApp")
                   packageServicesAndUploadToRepo("SMAS_SLLDT","CI_Staging","SParent.WebAPI")
                }
            } else {
                echo "package to upload to server server test"
                stage('Packaging Module Web and Uploader'){
                    packageServicesAndUploadToRepo("SMAS_SLLDT","CI_feature","SParentWebApp")
                    packageServicesAndUploadToRepo("SMAS_SLLDT","CI_feature","SParent.WebAPI")
                }
            }
        }
    }
    parallel tasks
    if(env.gitlabBranch == env.STAGING_BRANCH) {
        echo "Deploy to Server Staging"
        stage('Deploy to Server Staging'){
        //    deploy_module_web("test", "SMAS_SLLDT", "CI_Staging")
        }
    } else {
        echo "Deploy to Server server test"
        stage('Deploy to Server server test'){
        //    deploy_module_web("test", "SMAS_SLLDT", "CI_feature")
        }
    }
    currentBuild.result = "SUCCESS"
}
def buildMergeRequest() {

    def stagingPublicIP = "10.60.155.242"

    def tasks = [:]
    tasks['unitTestAndCodeCoverage'] = {
        node("slave_43") {
            echo "Unit Test"
            unitTestAndCodeCoverage("MERGE")
        }
    }
    tasks['SonarQube Scan'] = {
        node("slave_43") {
           echo "test sonar"
           sonarQubeScan("MERGE")
        }
    }
    tasks['Package and Build Artifact'] = {
        node("slave_203") {
            stage ('Build'){
                buildService("MERGE")
            }
            stage('Packaging Module Web and Uploader'){
                packageServicesAndUploadToRepo("SMAS_SLLDT","CI_Staging","SParentWebApp")
                packageServicesAndUploadToRepo("SMAS_SLLDT","CI_Staging","SParent.WebAPI")
            }
        }
    }
    parallel tasks
    if (env.gitlabTargetBranch == env.STAGING_BRANCH){
        stage('Deploy to server staging'){
            node('slave_43'){
                deploy_module_web("test", "SMAS_SLLDT", "CI_Staging")
            }
        }
    }
    stage('Automations Test in Staging'){
            sleep(120)
            build job: ''
    }
    // stage('Performance Test in Staging'){
    //     build job: ''
    // }
    currentBuild.result = "SUCCESS"
}

return [
    buildPushCommit      : this.&buildPushCommit,
    buildMergeRequest    : this.&buildMergeRequest,
    buildAcceptAndCloseMR: this.&buildAcceptAndCloseMR

]
