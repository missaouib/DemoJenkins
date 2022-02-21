env.pre_production_ip = "10.60.155.244"
env.pre_production_hompage_port = "8080"
env.test_ip = "10.60.108.43"
env.test_hompage_port = "8089"
env.config_git_uri = "http://10.60.156.11/hungbd2/TCQG.git"
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

    stage("Get & check Production ENV") {
        loadGitConfig(env.config_git_uri, env.config_git_branch, "")
        env.DEPLOY_RESULT_DESCRIPTION += "<h5>- Cloud Config Git Version: ${env.config_git_branch}</h5>"
    }
    updateGitlabCommitStatus name: "deploy to production ", state: 'running'
    def tasks = [:]
    tasks['SonarQubeScan'] = {
        node("slave_43") {
            echo "Scanning SonaQube to upcode"
        //    jenkinsfile_CI.sonarQubeScan("PUSH")
        }
    }
    tasks['Build and Packaging pre upcode'] = {
        node("slave_203") {
            stage ('Build'){
                jenkinsfile_CI.buildSerrvice("PUSH")
            }
            stage('Packaging Module Web and Uploader'){
                jenkinsfile_CI.packageServicesAndUploadToRepo("TCQG_CD","Web", true)
            }
            // stage('Packaging Module Business and Uploader'){
            //     jenkinsfile_CI.packageServicesAndUploadToRepo("TCQG_CI","Business", false)
            // }
        }
    }
    parallel tasks
    stage("Deploy application module web to environment test, staging and notify to maintainer") {
        node('slave_43'){
            dir('/u01/jenkins/workspace/TCQG/CD'){
               jenkinsfile_CI.deploy_module_web("test","TCQG_CD", "Web")
               jenkinsfile_CI.deploy_module_web("staging","TCQG_CD", "Web")
            }
            
        }
        // def deployResultTitle = "Deploy module web into branch ${env.config_git_branch} " +
        //         "to environment test & staging Result"
        // def deployResultDescription = "Deploy into branch with tag ${env.config_git_branch} " +
        //         "is deployed to environment Test & Staging at address: " +
        //         "<a href='${env.test_ip}:${env.test_hompage_port}'>" +
        //         "<h2>Test environment: <a href='http://${env.test_ip}:${env.test_hompage_port}'>" +
        //         "http://${env.test_ip}:${env.test_hompage_port}</a></h2>" +
        //         "<a href='${env.pre_production_ip}:${env.pre_production_hompage_port}'>" +
        //         "<h2>Staging environment: <a href='http://${env.pre_production_ip}:${env.pre_production_hompage_port}'>" +
        //         "http://${env.pre_production_ip}:${env.pre_production_hompage_port}</a></h2>"
        // deployResultDescription += "<h5>- CommitID: ${env.GIT_COMMIT_ID}</h5>"
        // deployResultDescription += "<h5>- Config Git Version: ${env.config_git_branch}</h5>"
        // deployResultDescription += "<ul style='list-style-type:disc'>"

        // deployResultDescription += "</ul>"
        // deployResultDescription += "<h4>Please check if enviroment Staging is accepted and decide " +
        //         "deploy this version to production environment or not by select at:</h4>" +
        //         "<h2><i><a href='${env.BUILD_URL}display/redirect'>" +
        //         "Deploy Process Details...</a></i></h2>" +
        //         "Running automation testing" +
        //         "<h4>Deploy to production process will be aborted after 24 hours from this message.</h4>"
        // createIssueAndMentionMaintainer(deployResultTitle, deployResultDescription)
    }
    stage('Funtions Testing in Staging'){
        sleep(120)
        build job: 'Autotest_TiemChung'
    }
    stage('Performance Test in Staging'){
        build job: 'autoperf_tiemchung_staging'
        def deployResultTitle = "Deploy module web into branch ${env.config_git_branch} " +
                "to environment test & staging Result"
        def deployResultDescription = "Deploy into branch with tag ${env.config_git_branch} " +
                "is deployed to environment Test & Staging at address: " +
                "<a href='${env.test_ip}:${env.test_hompage_port}'>" +
                "<h2>Test environment: <a href='http://${env.test_ip}:${env.test_hompage_port}'>" +
                "http://${env.test_ip}:${env.test_hompage_port}</a></h2>" +
                "<a href='${env.pre_production_ip}:${env.pre_production_hompage_port}'>" +
                "<h2>Staging environment: <a href='http://${env.pre_production_ip}:${env.pre_production_hompage_port}'>" +
                "http://${env.pre_production_ip}:${env.pre_production_hompage_port}</a></h2>"
        deployResultDescription += "<h5>- CommitID: ${env.GIT_COMMIT_ID}</h5>"
        deployResultDescription += "<h5>- Config Git Version: ${env.config_git_branch}</h5>"
        deployResultDescription += "<ul style='list-style-type:disc'>"

        deployResultDescription += "</ul>"
        deployResultDescription += "<h4>Please check if enviroment Staging is accepted and decide " +
                "deploy this version to production environment or not by select at:</h4>" +
                "<h2><i><a href='${env.BUILD_URL}display/redirect'>" +
                "Deploy Process Details...</a></i></h2>" +
                "<h4>Deploy to production process will be aborted after 24 hours from this message.</h4>"
        createIssueAndMentionMaintainer(deployResultTitle, deployResultDescription)
    }
    def deployInput = "Deploy"

    stage("Wait for maintainer accept or reject to deploy to production") {
        try {
            timeout(time: 24, unit: 'HOURS') {
                deployInput = input(
                    submitter : 'hoanv25',
                    // submitter: "${env.project_maintainer_list}",
                    message: 'Pause for wait maintainer selection', ok: "Execute", parameters: [
                    choice(choices: ['Deploy', 'Abort'], description: 'Deploy to production or abort deploy process?', name: 'DEPLOY_CHOICES')
                ])
            }
            updateGitlabCommitStatus name: "deploy to production ", state: 'running'
        } catch (err) { // timeout reached or input false
            echo "Exception"
            def user = err.getCauses()[0].getUser()
            if ('SYSTEM' == user.toString()) { // SYSTEM means timeout.
                echo "Timeout is exceeded!"
            } else {
                echo "Aborted by: [${user}]"
            }
            deployInput = "Abort"

        }
        echo "Input value: $deployInput"
    }

    if (deployInput == "Deploy") {
        try {
            echo "Running"
            updateGitlabCommitStatus name: "deploy to production ", state: 'running'
        } catch (Exception e) {
            echo "Error: ${e.toString()}. Rollback!"
            env.DEPLOY_RESULT_DESCRIPTION += "<h4>Error Occur when Deploy Service. Rollback to latest success version</h4>"
            // rollBackDeployServers(serviceListToUpgrade)
            error "Deploy Failed."
        } finally {
            echo 'productions deployment'
            env.DEPLOY_RESULT_DESCRIPTION += "<h4>Deploy to productions. Please check service</h4>"
            // Thêm môi trường production vào đây
            stage('Deploy to server product'){
                node('slave_43'){
                    dir('/u01/jenkins/workspace/TCQG/CD'){
                        sh " sudo ansible-playbook deploy_to_productions_app47.yml -e VERSION=${BUILD_NUMBER} -e GROUPID=TCQG_CD_Web -vvv"
                    }   
                } 
            }
        }
        currentBuild.result = "SUCCESS"
    } else { // Deploy input is Abort
        stage("Cancel deploy process") {
            echo "Deploy process is canceled."
            updateGitlabCommitStatus name: "deploy to production ", state: 'canceled'
            currentBuild.result = "ABORTED"
            env.DEPLOY_RESULT_DESCRIPTION += "<h4>:warning: Deploy Final Result: Aborted.<h4>"
        }
    }
}
def rollBackTag() {
    def gitTag = ''
    def serviceList = []
    env.DEPLOY_RESULT_DESCRIPTION += "<h4>Test & Verify Phase Result</h4>"
    stage('Wait for user submit Tag to rollback') {
        try {
            timeout(time: 24, unit: 'HOURS') {
                gitTag = input(
                    submitter: "${env.ROLLBACK_MAINTAINER_LIST}",
                    message: 'Pause for wait maintainer selection', ok: "Rollback", parameters: [
                    string(defaultValue: '',
                        description: 'Tag to rollback',
                        name: 'Tag')
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
            gitTag = 'Aborted'
        }
    }
    def statusCode = sh(script: "git show-ref --verify refs/tags/${gitTag}", returnStatus: true)
    env.GIT_TAG_ROLLBACK = gitTag
    if (gitTag == 'Aborted' || gitTag == '') {
        stage("Cancel deploy process") {
            echo "Deploy process is canceled."
            currentBuild.result = "ABORTED"
        }
    } else {
        if (statusCode == 0) {
            stage("Checkout Source Code") {
                sh "ls -la"
                sh "git checkout -b ${gitTag} ${gitTag}"
                sh "ls -la"
                def commitIdStdOut = sh(script: 'git rev-parse HEAD', returnStdout: true)
                env.DEPLOY_GIT_COMMIT_ID = commitIdStdOut.trim()
            }
            env.DEPLOY_RESULT_DESCRIPTION += "<h4>Deploy Phase Result</h4>"
            stage("Get & check Production ENV") {
                def semantic_version = gitTag.split("\\.")
                env.config_git_branch = "${semantic_version[0]}.${semantic_version[1]}"
                echo("config git branch: ${env.config_git_branch}")
                loadGitConfig(env.config_git_uri, env.config_git_branch, "")
                env.DEPLOY_RESULT_DESCRIPTION += "<h5>- Config Git Version: ${env.config_git_branch}</h5>"
            }
            try {
                genImportantFileAndDeploy(moduleVersionList, serviceList, serviceList, env.config_git_branch)
            } catch (err) {
                echo "Error: ${err.toString()}. Rollback to latest version!"
                env.DEPLOY_RESULT_DESCRIPTION += "<h4>Error Occur when RollBack Service. Rollback to latest success version</h4>"
                rollBackDeployServers(serviceList)
                error "Rollback to tag ${gitTag} Failed."
            } finally {
                stage("Resume Stack AutoScaling") {
                    resumeHeatStackAutoScaling()
                }
            }
        } else error("Invalid git tag ${gitTag}")
    }
}

def loadGitConfig(String cloudConfigGitURI, String branch, String gitConfigCommitID) {
    sh 'rm -rf git-config'
    sh 'mkdir git-config'
    echo "branch : ${branch}"
    echo "cloudConfigURI : ${cloudConfigGitURI}"
    dir('git-config') {
        checkout changelog: true, poll: true, scm: [
            $class                           : 'GitSCM',
            branches                         : [[name: "${branch}"]],
            doGenerateSubmoduleConfigurations: false,
            extensions                       : [[$class: 'UserIdentity', email: 'hienptt22@viettel.com.vn', name: 'hienptt22']],
            submoduleCfg                     : [],
            userRemoteConfigs                : [[credentialsId: '63265de3-8396-40f9-803e-5cd0b694e519',
                                                 url          : "${cloudConfigGitURI}"]]
        ]

        def commitIdStdOut = sh(script: 'git rev-parse HEAD', returnStdout: true)
        env.CONFIG_GIT_COMMIT_ID = commitIdStdOut.trim()
        echo "cloud config git repo commit id : ${env.CONFIG_GIT_COMMIT_ID}"

        if (gitConfigCommitID != "") {
            sh "git checkout -b lastGitConfig ${gitConfigCommitID}"
        }
    }
    def commitIdStdOut = sh(script: 'git rev-parse HEAD', returnStdout: true)
    env.GIT_COMMIT_ID = commitIdStdOut.trim()
    echo "git source code commit id : ${env.GIT_COMMIT_ID}"
}
def createIssueAndMentionMaintainer(issueTitle, issueDescription) {
    echo "issueTitle: ${issueTitle}"
    echo "issueDescription: ${issueDescription}"
   withCredentials([usernamePassword(credentialsId: 'a5eedd9f-332d-4575-9756-c358bbd808eb', usernameVariable: 'user',
              passwordVariable: 'password')]){
        def issueContentJson = """
                                    {
                                        "title": "${issueTitle}",
                                        "description": "${issueDescription}",
                                        "labels": "Deploy Result"
                                    }
                                """
        echo "issueContentJson: ${issueContentJson}"
        def createIssueResp = httpRequest([
            acceptType   : 'APPLICATION_JSON',
            httpMode     : 'POST',
            contentType  : 'APPLICATION_JSON',
            customHeaders: [[name: "PRIVATE-TOKEN", value: password ]],
            url          : "${env.GITLAB_PROJECT_API_URL}/issues",
            requestBody  : issueContentJson

        ])
        def notifyMemberLevel = 40
        def projectMemberList = jenkinsfile_utils.getProjectMember(notifyMemberLevel)
        def issueCommentStr = ""
        for (member in projectMemberList) {
            issueCommentStr += "@${member} "
        }
        def issueCreated = jenkinsfile_utils.jsonParse(createIssueResp.content)
        def issueCommentJson = """
                                    {
                                        "body": "${issueCommentStr}"
                                    }
                                """
        httpRequest([
            acceptType   : 'APPLICATION_JSON',
            httpMode     : 'POST',
            contentType  : 'APPLICATION_JSON',
            customHeaders: [[name: "PRIVATE-TOKEN", value: password ]],
            url          : "${env.GITLAB_PROJECT_API_URL}/issues/${issueCreated["iid"]}/notes",
            requestBody  : issueCommentJson
        ])
    }
}
def rollBackDeployServers() {
    try {
        if (lastSuccessDeploymentInfo["latest-cloud-config-git-commit-id"] == null && lastSuccessDeploymentInfo["latest-deploy-git-commit-id"] == null) {
            env.DEPLOY_RESULT_DESCRIPTION += "<h4>Latest success version doesn't exist. Rollback failed.</h4>"
        } else {
            env.DEPLOY_RESULT_DESCRIPTION += "<h4>Roll back info: </h4>"
            env.DEPLOY_RESULT_DESCRIPTION += "<ul style='list-style-type:disc'>"
            env.DEPLOY_RESULT_DESCRIPTION += "<li>Roll backed git version: ${lastSuccessDeploymentInfo['latest-deploy-git-commit-id']}</li>"
            env.DEPLOY_RESULT_DESCRIPTION += "<li>Roll backed git config version: ${lastSuccessDeploymentInfo['latest-cloud-config-git-commit-id']}</li>"
            env.DEPLOY_RESULT_DESCRIPTION += "</ul>"

            stage("Load last git config") {
                loadGitConfig(env.config_git_uri, env.config_git_branch, lastSuccessDeploymentInfo["latest-cloud-config-git-commit-id"])
            }
        }
    } catch (Exception e) {
        echo "${e.toString()}"
        env.DEPLOY_RESULT_DESCRIPTION += "<h4>:x: Roll back result: Roll back failed.</h4>"
        error "Roll back failed."
    }
}

def toList(value) {
    return [value].flatten().findAll { it != null }
}
return [
    buildPushCommit      : this.&buildPushCommit,
    buildMergeRequest    : this.&buildMergeRequest,
    deployToProduction: this.&deployToProduction,
    rollBackTag       : this.&rollBackTag
]

