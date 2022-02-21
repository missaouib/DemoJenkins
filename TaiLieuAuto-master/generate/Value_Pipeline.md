# Một số hướng dẫn để viết pipeline trong jenkins

`Hướng dẫn một số enviroment có thể tích hợp jenkins, gitlab`

## Hướng dẫn checkout source code

```shell
   def checkoutSourceCode(checkoutType){
    if (checkoutType == "PUSH"){
        checkout changelog: true, poll: true, scm: [
            $class                           :  'GitSCM',
            branches                         : [[name: "${env.gitlabAfter}"]],
            doGenerateSubmoduleConfigurations: false,
            extensions                       : [[$class: 'UserIdentity',
                                                 email : 'Nhap email', name: 'Nhap ten'],
                                                [$class: 'CleanBeforeCheckout']],
            submoduleCfg                     : [],
            userRemoteConfigs                : [[credentialsId: "Nhap credential user gitlab tren jenkins",
                                                 url          : "${env.gitlabSourceRepoHomepage}" +".git"]]
        ]
    } else if (checkoutType == "MERGE") {
        checkout changelog: true, poll: true, scm: [
            $class                           : 'GitSCM',
            branches                         : [[name: "origin/${env.gitlabSourceBranch}"]],
            doGenerateSubmoduleConfigurations: false,
            extensions                       : [[$class : 'PreBuildMerge',
                                                 options: [
                                                     fastForwardMode: 'FF',
                                                     mergeRemote    : 'origin',
                                                     mergeStrategy  : 'RESOLVE',
                                                     mergeTarget    : "${env.gitlabTargetBranch}"
                                                 ]],
                                                [$class: 'UserIdentity',
                                                    email : 'Nhap email', name: 'Nhap ten'],
                                                [$class: 'CleanBeforeCheckout']],
            submoduleCfg                     : [],
            userRemoteConfigs                : [[credentialsId: "Nhap credential user gitlab tren jenkins",
                                                 url          : "${env.gitlabSourceRepoHomepage}" + ".git"]]
        ]
    }
}
```

## Hướng dẫn lấy member trong project gitlab

```shell
    def getProjectMember(notifyMemberLevel){
    def project_members = []
    def project_member_notify = []
     withCredentials([usernamePassword(credentialsId: 'a5eedd9f-332d-4575-9756-c358bbd808eb', usernameVariable: 'user',
              passwordVariable: 'password')]){
        def currentPage = 1
        haveNextPage = true
        while (haveNextPage) {
            def response = httpRequest([
                acceptType   : 'APPLICATION_JSON',
                httpMode     : 'GET',
                contentType  : 'APPLICATION_JSON',
                customHeaders: [[name: 'Private-Token', value: password ]],
                url          : "${env.GITLAB_PROJECT_API_URL}/members?per_page=100&page=${currentPage}"
            ])

            def project_members_resp = jenkinsfile_utils.jsonParse(response.content)
            project_members.addAll(project_members_resp)
            if (project_members_resp.size() == 0) {
                haveNextPage = false
            } else {
                currentPage += 1
            }
        }
    }
    for (member in project_members) {
        if (member['access_level'].toInteger() >= notifyMemberLevel) {
            if (!project_member_notify.contains(member['username'])) {
                project_member_notify.add(member['username'])
            }
        }
    }
    return project_member_notify
}
```

## Hướng dẫn SonarQue và lấy kết quả quét sonar

```shell
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
```

## Hướng dẫn Lấy kết quả unit test

```shell
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
```

## Hướng dẫn viết hàm quét sonar, unit Test, Coverage

```shell
    def sonarQubeScan(buildType) {
    stage("Checkout Source Code") {
        jenkinsfile_utils.checkoutSourceCode(buildType)
        echo  'Checkout source code'
    }
    stage("Unit Test & Code Coverage Test") {
             try {
            echo "code coverage started"
            sh '/home/app/server/apache-maven-3.2.3/bin/mvn clean test org.jacoco:jacoco-maven-plugin:0.8.2:report-aggregate'
            echo "code coverage done"
            jacoco classPattern: 'auth-service/target/classes,invoice-service/target/classes,categories-service/target/classes,kpilog-service/target/classes,media-service/target/classes,notification-service/target/classes,payment-service/target/classes',
                   sourcePattern: 'categories-service/src/main/java,auth-service/src/main/java,invoice-service/src/main/java,kpilog-service/src/main/java,media-service/src/main/java,notification-service/src/main/java,payment-service/src/main/java'

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
            junit '*/target/*-reports/TEST-*.xml'
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
                    "-Dsonar.exclusions=**/*.zip,**/*.jar,**/*.html,**/build/**,**/target/**,**/.settings/**,**/.mvn/**"
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
```

## Hướng dẫn viết pipeline quét sonar với file properties

```shell
	withSonarQubeEnv('SONARQ_V6'){
            sh "/home/app/server/sonar-scanner/bin/sonar-scanner " +
                "-Dproject.settings==./sonar-project.properties " +
                "-Dsonar.java.binaries=. "
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
```

## Hướng dẫn viết stage input version by maintainer

```shell
    def deployToProduction() {
    def gitlabBranch = env.gitlabBranch
    def semantic_version = gitlabBranch.split("/")[2].split("\\.")
    env.config_git_branch = "${semantic_version[0]}.${semantic_version[1]}"
    echo "Config git branch: ${env.config_git_branch}"
    env.DEPLOY_RESULT_DESCRIPTION += "<h4>Test & Verify Phase Result</h4>"
    updateGitlabCommitStatus name: "deploy to production ", state: 'running'
    stage("Checkout Source Code") {
        jenkinsfile_utils.checkoutSourceCode("PUSH")
        def commitIdStdOut = sh(script: 'git rev-parse HEAD', returnStdout: true)
        env.DEPLOY_GIT_COMMIT_ID = commitIdStdOut.trim()
    }
    env.DEPLOY_RESULT_DESCRIPTION += "<h4>Deploy Phase Result</h4>"
    updateGitlabCommitStatus name: "deploy to production ", state: 'running'
    def version = ''
    stage('Wait for maintainer submiter version to deploy'){
        updateGitlabCommitStatus name: "deploy to production ", state: 'running'
        try {
            timeout(time: 24, unit: 'HOURS') {
                version = input(
                    submitter: "user",
                    message: 'Pause for wait maintainer selection', ok: "Deploy", parameters: [
                    string(defaultValue: '',
                        description: 'version to deploy',
                        name: 'Version')
                ])
            }
        } catch (err) {
            echo "Exception"
            def user = err.getCauses()[0].getUser()
            if ('SYSTEM' == user.toString()) { // SYSTEM means timeout.
                echo "Timeout is exceeded!"
            } else {
                echo "Aborted by: [${user}]"
            }
            version = 'Aborted'
        }
        echo "$version"
        env.project_version = version
    }
    if (version == 'Aborted' || version == '') {
        stage("Cancel deploy process") {
            echo "Deploy process is canceled."
            currentBuild.result = "ABORTED"
        }
    } else {
        stage("Deploy to production  with version build number $version"){
            echo "Build version $version"
            node('slave_43'){
                dir('/u01/jenkins/workspace/TCQG/CD'){
                    sh " sudo ansible-playbook deploy.yml -e VERSION=${version} -e GROUPID=TCQG_CI_Web -vvv"
                }
            }
        }

        currentBuild.result = "SUCCESS"
    }
}
```

## Get comment commit git

```shell

	git log -1 | tail -1 | awk '{print $1}'
```
