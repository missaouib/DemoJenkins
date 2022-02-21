def deployToProduction() {
    def gitlabBranch = env.gitlabBranch
    def semantic_version = gitlabBranch.split("/")
    env.config_git_branch = semantic_version
    echo "Config git branch: ${env.config_git_branch}"
    env.DEPLOY_RESULT_DESCRIPTION += "<h4>Test & Verify Phase Result</h4>"
    updateGitlabCommitStatus name: "deploy to production ", state: 'running'
    stage("Checkout Source Code") {
        jenkinsfile_utils.checkoutSourceCode("PUSH")
        def commitIdStdOut = sh(script: 'git rev-parse HEAD', returnStdout: true)
        env.DEPLOY_GIT_COMMIT_ID = commitIdStdOut.trim()
    }
    stage("2. Check result jenkins pipline for commit Tag"){
        withCredentials([
        usernamePassword(credentialsId: 'a5eedd9f-332d-4575-9756-c358bbd808eb', usernameVariable: 'username', passwordVariable: 'password'),
        usernamePassword(credentialsId: 'jenkins_api_token_new', usernameVariable: 'usernamejenkins', passwordVariable: 'token')
        ]) {
            def pipelines = httpRequest([
            acceptType   : 'APPLICATION_JSON',
            httpMode     : 'GET',
            contentType  : 'APPLICATION_JSON',
            customHeaders: [[name: 'Private-Token', value: password]],
            url          : "${env.GITLAB_PROJECT_API_URL}/repository/commits/$env.DEPLOY_GIT_COMMIT_ID/statuses?all=yes&ref=master"
            ])
            for (pipeline in jenkinsfile_utils.jsonParse(pipelines.content)) {
                def result= pipeline['status']
                def buildURL = pipeline["target_url"].substring(0, pipeline["target_url"].length() - 17)
                echo "Check buildURL: ${buildURL}"
                echo "result pipeline: $result"
            }
        }
    }
    env.DEPLOY_RESULT_DESCRIPTION += "<h4>Deploy Phase Result</h4>"
    updateGitlabCommitStatus name: "deploy to production ", state: 'running'
    def deployInput = "Deploy"
    stage('Wait for maintainer submiter version to deploy'){
        updateGitlabCommitStatus name: "deploy to production ", state: 'running'
        try {
            timeout(time: 24, unit: 'HOURS') {
                deployInput = input(
                    submitter: "${env.project_maintainer_list}",
                    submitterParameter: 'submitter',
                    message: 'Pause for wait maintainer selection', ok: "Execute", parameters: [
                    choice(choices: ['Deploy', 'Abort'], description: 'Deploy to production or abort deploy process?', name: 'DEPLOY_CHOICES')
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
            deployInput = "Abort"
        }
        echo "Input value: $deployInput"

    }
    if (version == 'Aborted' || version == '') {
        stage("Cancel deploy process") {
            echo "Deploy process is canceled."
            currentBuild.result = "ABORTED"
        }
    } else {
        stage("Deploy to production with version build number $version"){
            echo "Build version $version"
            node('slave_43'){
                echo "deploy"
            }
        }

        currentBuild.result = "SUCCESS"
    }
}
def deployToPreRelease() {
    def gitlabBranch = env.gitlabBranch
    echo "Branch : ${gitlabBranch}"
    def semantic_version = gitlabBranch.split("/")[2]
    env.config_git_branch = semantic_version
    echo "Config git branch: ${env.config_git_branch}"
    env.DEPLOY_RESULT_DESCRIPTION += "<h4>Test & Verify Phase Result</h4>"
    updateGitlabCommitStatus name: "deploy to production ", state: 'running'
    stage("1. Checkout Source Code") {
        jenkinsfile_utils.checkoutSourceCode("PUSH")
        def commitIdStdOut = sh(script: 'git rev-parse HEAD', returnStdout: true)
        env.DEPLOY_GIT_COMMIT_ID = commitIdStdOut.trim()
    }
    stage("2. Check result jenkins pipline for commit Tag"){
        withCredentials([
        usernamePassword(credentialsId: '63265de3-8396-40f9-803e-5cd0b694e519', usernameVariable: 'username', passwordVariable: 'password'),
        usernamePassword(credentialsId: 'jenkins_api_token_new', usernameVariable: 'usernamejenkins', passwordVariable: 'token')
        ]) {
            def pipelines = httpRequest([
            acceptType   : 'APPLICATION_JSON',
            httpMode     : 'GET',
            contentType  : 'APPLICATION_JSON',
            customHeaders: [[name: 'Private-Token', value: password]],
            url          : "${env.GITLAB_PROJECT_API_URL}/repository/commits/$env.DEPLOY_GIT_COMMIT_ID/statuses?all=yes&ref=master"
            ])
            for (pipeline in jenkinsfile_utils.jsonParse(pipelines.content)) {
                def result= pipeline['status']
                def buildURL = pipeline["target_url"].substring(0, pipeline["target_url"].length() - 17)
                echo "Check buildURL: ${buildURL}"
                echo "result pipeline: $result"
            }
        }
    }
    env.DEPLOY_RESULT_DESCRIPTION += "<h4>Deploy Phase Result</h4>"
    updateGitlabCommitStatus name: "deploy to production ", state: 'running'
    def deployInput = ""
    def deployer = ""

    stage("3. Wait for maintainer accept or reject to deploy to production") {
        try {
            deployer = env.project_maintainer_list
            echo "project_maintainer_list: ${env.project_maintainer_list}"

            timeout(time: 24, unit: 'HOURS') {
                deployInput = input(
                    submitter: "${deployer}",
                    submitterParameter: 'submitter',
                    message: 'Pause for wait maintainer selection', ok: "Deploy", parameters: [
                    string(defaultValue: '',
                        description: 'Version to Deploy',
                        name: 'Deploy')
                ])
            }
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
    env.GIT_TAG_DEPLOY = deployInput.Deploy
    echo "Deploy Version: ${env.GIT_TAG_DEPLOY}"
    env.GIT_TAG_DEPLOY = deployInput['Deploy']
    echo "Deploy Version: $env.GIT_TAG_DEPLOY"
    env.project_version = "${env.GIT_TAG_DEPLOY}_GitTag_${env.config_git_branch}"
    def buildNumberStaging= ''
    def resultStaging = ''
        if(deployer.contains(deployInput.submitter)){
            stage("Check job CI status"){
                buildNumberStaging=sh(returnStdout: true,script:"echo $env.GIT_TAG_DEPLOY | sed \'s/.*u//\'").trim()
                echo "buildNumberStaging: $buildNumberStaging"
                withCredentials([usernamePassword(credentialsId: 'jenkins_api_token_new', usernameVariable: 'usernamejenkins', passwordVariable: 'token')]) {
                def buildInfoResp = httpRequest([
                    acceptType   : 'APPLICATION_JSON',
                    httpMode     : 'GET',
                    contentType  : 'APPLICATION_JSON',
                    authentication: 'jenkins_api_token_new',
                    url          : "${env.buildUrlStaging}/$buildNumberStaging/api/json"
                ])
                resultStaging = jenkinsfile_utils.jsonParse(buildInfoResp.content)["result"]
                echo "Result: $resultStaging"
                }
                if(resultStaging != 'SUCCESS'){
                    error "Job run staging failure. Please check job ${env.buildUrlStaging}/$buildNumberStaging"
                }else{
                    echo "Job staging run success"
                }
            }
            stage("4. Execute DB update"){
                echo "update to DB"
            }
            stage('5. Pull images and push'){
			//    withCredentials([usernamePassword(credentialsId: 'duybvk-docker', usernameVariable: 'username',
            //         passwordVariable: 'password')]){
            //         sh """
            //             docker --config ~/.docker/.duybvk login -u ${username} -p '${password}' 10.60.156.72
            //             docker --config ~/.docker/.duybvk pull 10.60.156.72/etc/crm-backend:$env.GIT_TAG_DEPLOY
            //             docker tag 10.60.156.72/etc/crm-backend:$env.GIT_TAG_DEPLOY 10.254.247.44/etc/crm-backend:${env.project_version}
            //         """
            //     }

                // withCredentials([usernamePassword(credentialsId: 'truongdx8-habor-196', usernameVariable: 'username',
                //     passwordVariable: 'password')]){
                // sh """
                //     docker --config ~/.docker/.truongdx8_habor_244 login -u ${username} -p '${password}' 10.254.247.44
                //     docker --config ~/.docker/.truongdx8_habor_244 push 10.254.247.44/etc/crm-backend:${env.project_version}
                //     docker rmi 10.254.247.44/etc/crm-backend:${env.project_version}
                // """
                // }
            }
            stage('6. Deploy to Productions'){
                echo "Tag: ${env.GIT_TAG_DEPLOY}"
                echo "Version Deploy: ${env.project_version}"
            }
            currentBuild.result = "SUCCESS"
        }else {
            stage("Cancel deploy process") {
                echo "Version: ${env.project_version}"
                echo "Deploy process is canceled."
                currentBuild.result = "ABORTED"
            }
        }
}
def rollBackTag() {
    def versionRollBack = ''
    env.DEPLOY_RESULT_DESCRIPTION += "<h4>Test & Verify Phase Result</h4>"
    stage('Wait for user submit Version to rollback') {
        try {
            timeout(time: 24, unit: 'HOURS') {
                versionRollBack = input(
                    submitter: "${env.ROLLBACK_MAINTAINER_LIST}",
                    message: 'Pause for wait maintainer selection', ok: "Rollback", parameters: [
                    string(defaultValue: '',
                        description: 'Version to rollback',
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
            versionRollBack = 'Aborted'
        }
    }
    // def statusCode = sh(script: "git show-ref --verify refs/tags/${gitTag}", returnStatus: true)
    env.VERSION_ROLLBACK = versionRollBack
    echo "Version: $versionRollBack"
    if (versionRollBack == 'Aborted' || versionRollBack == '') {
        stage("Cancel deploy process") {
            echo "Version: $versionRollBack"
            echo "Deploy process is canceled."
            currentBuild.result = "ABORTED"
        }
    } else {
        stage("Rollback in production with version build number $versionRollBack"){
            echo "Build version rollback $versionRollBack"
            node('slave_43'){
                    echo "Rollback with version $versionRollBack"
            }
        }
        currentBuild.result = "SUCCESS"
    }
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

def toList(value) {
    return [value].flatten().findAll { it != null }
}
return [
    buildPushCommit      : this.&buildPushCommit,
    buildMergeRequest    : this.&buildMergeRequest,
    deployToProduction: this.&deployToProduction,
    rollBackTag       : this.&rollBackTag
]

