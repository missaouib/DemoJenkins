def deployToProduction() {
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
        if(deployer.contains(deployInput.submitter)){
            stage("4. Execute DB update"){
                if(env.enableDBProd == "true"){
                    build job: "$env.jobDBVersioning", parameters: [
                        string(name: 'DB_TYPE', value: 'oracle'), 
                        string(name: 'ENVIRONMENT_RUN', value: "PROD"), 
                        string(name: 'GITLABURL', value: "$env.GITLABURL"), 
                        string(name: 'SCHEMA', value: "$env.SCHEMA"), 
                        string(name: 'defaultFile', value: "$env.defaultFile"),
                        string(name: 'VERSION', value: "$env.project_version")
                    ]
                } else {
                    echo "skip compare version DB"
                }
            }
            stage('5. Pull images and push to habor 10.60.117.196'){
                withCredentials([usernamePassword(credentialsId: 'duybvk-docker', usernameVariable: 'username',
                    passwordVariable: 'password')]){
                    sh """
                        docker --config ~/.docker/.duybvk login -u ${username} -p '${password}' 10.60.156.72
                        docker --config ~/.docker/.duybvk pull 10.60.156.72/etc/dmdc-backend:$env.GIT_TAG_DEPLOY
                        docker tag 10.60.156.72/etc/dmdc-backend:$env.GIT_TAG_DEPLOY 10.60.117.196/etc/dmdc-backend:${env.project_version}
                    """
                }
                withCredentials([usernamePassword(credentialsId: 'truongdx8-habor-196', usernameVariable: 'username',
                    passwordVariable: 'password')]){
                sh """
                    docker --config ~/.docker/.truongdx8_habor_196 login -u ${username} -p '${password}' 10.60.117.196
                    docker --config ~/.docker/.truongdx8_habor_196 push 10.60.117.196/etc/dmdc-backend:${env.project_version}
                    docker rmi 10.60.117.196/etc/dmdc-backend:${env.project_version}
                """
                }
            }
            stage('6. Deploy to Productions'){
                echo "Tag: ${env.GIT_TAG_DEPLOY}"
                jenkinsfile_CI.release2k8s("${env.project_version}","etc-idc","dmdc-backend","config_117_191")
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
def deployToProductionVTNet() {
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
    def buildNumberStaging= ''
    def resultStaging = ''
    env.project_version = "${env.GIT_TAG_DEPLOY}_GitTag_${env.config_git_branch}"
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
                if(env.enableDBProd == "true"){
                    build job: "$env.jobDBVersioning", parameters: [
                        string(name: 'DB_TYPE', value: 'oracle'), 
                        string(name: 'ENVIRONMENT_RUN', value: "PROD"), 
                        string(name: 'GITLABURL', value: "$env.GITLABURL"), 
                        string(name: 'SCHEMA', value: "$env.SCHEMA"), 
                        string(name: 'defaultFile', value: "$env.defaultFile"),
                        string(name: 'VERSION', value: "$env.project_version")
                    ]
                } else {
                    echo "skip compare version DB"
                }
            }
            stage('5. Pull images and push to habor 10.254.247.44'){
                withCredentials([usernamePassword(credentialsId: 'duybvk-docker', usernameVariable: 'username',
                    passwordVariable: 'password')]){
                    sh """
                        docker --config ~/.docker/.duybvk login -u ${username} -p '${password}' 10.60.156.72
                        docker --config ~/.docker/.duybvk pull 10.60.156.72/etc/dmdc-backend:$env.GIT_TAG_DEPLOY
                        docker tag 10.60.156.72/etc/dmdc-backend:$env.GIT_TAG_DEPLOY 10.254.247.44/etc/dmdc-backend:${env.project_version}
                    """
                }
                withCredentials([usernamePassword(credentialsId: 'truongdx8-habor-196', usernameVariable: 'username',
                    passwordVariable: 'password')]){
                sh """
                    docker --config ~/.docker/.truongdx8_habor_196 login -u ${username} -p '${password}' 10.254.247.44
                    docker --config ~/.docker/.truongdx8_habor_196 push 10.254.247.44/etc/dmdc-backend:${env.project_version}
                    docker rmi 10.254.247.44/etc/dmdc-backend:${env.project_version}
                """
                }
            }
            stage('6. Deploy to Productions'){
                echo "Tag: ${env.GIT_TAG_DEPLOY}"
                jenkinsfile_CI.release2k8s("${env.project_version}","etc-vtnet","dmdc-backend","config_247_11")
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
def deployToPreRelease() {
    def gitlabBranch = env.gitlabBranch
    echo "Branch : ${gitlabBranch}"
    def semantic_version = gitlabBranch.split("/")[2]
    println semantic_version
    env.config_git_branch = semantic_version
    echo "Config git branch: ${env.config_git_branch}"
    env.DEPLOY_RESULT_DESCRIPTION += "<h4>Test & Verify Phase Result</h4>"
    updateGitlabCommitStatus name: "deploy to pre production ", state: 'running'
    def service='dmdc-backend'
    def versionRunningStaging=''
    def buildNumberStaging=''
    def resultStaging=''
    stage("1. Checkout Source Code") {
        jenkinsfile_utils.checkoutSourceCode("PUSH")
        def commitIdStdOut = sh(script: 'git rev-parse HEAD', returnStdout: true)
        env.DEPLOY_GIT_COMMIT_ID = commitIdStdOut.trim()
    }
    stage("2. Check images running in staging and result run job jenkins"){
        sh """
            if [ -d "${service}-staging-release" ]; then
                rm -rf ${service}-staging-release;
            else mkdir ${service}-staging-release && echo "create folder"
            fi;
            cd ${service}-staging-release
        """
        checkout changelog: true, poll: true, scm: [
            $class                           :  'GitSCM',
            branches                         : [[name: "master"]],
            doGenerateSubmoduleConfigurations: false,
            extensions                       : [[$class: 'UserIdentity',
                                                email : 'duybvk@viettel.com.vn', name: 'duybvk'],
                                                [$class: 'CleanBeforeCheckout']],
            submoduleCfg                     : [],
            userRemoteConfigs                : [[credentialsId: "8cf687db-fdd1-450d-ab75-2aedbe5aec66",
                                                url          : "http://10.60.156.11/etc-2.0/etc-deployment" +".git"]]
        ]
        def folderDeploy= sh(script: 'pwd', returnStdout: true)
        env.buildFolderDeployResult = folderDeploy.trim()
        try {
            dir("$env.buildFolderDeployResult/k8s/etc-staging"){
                def POD_NAME = sh(returnStdout: true,script:"kubectl -n etc --kubeconfig=config_108_172 get pods --sort-by=.status.startTime | grep '$service' | tail -n 1 | awk '{print \$1}'").trim()
                echo "POD_NAME: $POD_NAME"
                versionRunningStaging=sh(returnStdout: true,script:"kubectl -n etc --kubeconfig=config_108_172 describe pods $POD_NAME | grep 'Image:' | grep -v 'filebeat' | awk '{print \$2'} | sed \'s/.*://\'").trim()
                echo "Version Running in Staging: $versionRunningStaging"
                buildNumberStaging=sh(returnStdout: true,script:"echo $versionRunningStaging | sed \'s/.*u//\'").trim()
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
        } catch(err){
            error "Job run staging failure. Please check job ${env.buildUrlStaging}/$buildNumberStaging"
        }
    }
    env.DEPLOY_RESULT_DESCRIPTION += "<h4>Deploy Phase Result</h4>"
    updateGitlabCommitStatus name: "deploy to pre production ", state: 'running'
    def deployInput = ""
    def deployer = ""
    env.project_version = "${versionRunningStaging}_GitTag_${env.config_git_branch}"
    stage("3. Execute DB update"){
        if(env.enableDBProd == "true"){
            build job: "$env.jobDBVersioning", parameters: [
                string(name: 'DB_TYPE', value: 'oracle'), 
                string(name: 'ENVIRONMENT_RUN', value: "PROD"), 
                string(name: 'GITLABURL', value: "$env.GITLABURL"), 
                string(name: 'SCHEMA', value: "$env.SCHEMA"), 
                string(name: 'defaultFile', value: "$env.defaultFile"),
                string(name: 'VERSION', value: "$env.project_version")
            ]
        } else {
            echo "skip compare version DB"
        }
    }
    stage('4. Pull images and push to habor 10.60.117.196'){
        withCredentials([usernamePassword(credentialsId: 'duybvk-docker', usernameVariable: 'username',
            passwordVariable: 'password')]){
            sh """
                docker --config ~/.docker/.duybvk login -u ${username} -p '${password}' 10.60.156.72
                docker --config ~/.docker/.duybvk pull 10.60.156.72/etc/dmdc-backend:$versionRunningStaging
                docker tag 10.60.156.72/etc/dmdc-backend:$versionRunningStaging 10.60.117.196/etc/dmdc-backend:${env.project_version}
            """
        }
        withCredentials([usernamePassword(credentialsId: 'truongdx8-habor-196', usernameVariable: 'username',
            passwordVariable: 'password')]){
        sh """
            docker --config ~/.docker/.truongdx8_habor_196 login -u ${username} -p '${password}' 10.60.117.196
            docker --config ~/.docker/.truongdx8_habor_196 push 10.60.117.196/etc/dmdc-backend:${env.project_version}
            docker rmi 10.60.117.196/etc/dmdc-backend:${env.project_version}
        """
        }
    }
    stage('5. Deploy to Pre release'){
        echo "Version: ${versionRunningStaging}"
        release2k8s("${env.project_version}","etc-idc","dmdc-backend","config_117_191","etc-release")
    }
    stage('6. Run Automations Test Pre-Release'){
        autoTestIDC()
    }
    currentBuild.result = "SUCCESS"
}
def release2k8s(version,enviroment,service,configFile,namespace){
    sh """
        if [ -d "${service}-pre-release" ]; then
            rm -rf ${service}-pre-release;
        else mkdir ${service}-pre-release && echo "create folder"
        fi;
        cd ${service}-pre-release
    """
    checkout changelog: true, poll: true, scm: [
        $class                           :  'GitSCM',
        branches                         : [[name: "master"]],
        doGenerateSubmoduleConfigurations: false,
        extensions                       : [[$class: 'UserIdentity',
                                            email : 'duybvk@viettel.com.vn', name: 'duybvk'],
                                            [$class: 'CleanBeforeCheckout']],
        submoduleCfg                     : [],
        userRemoteConfigs                : [[credentialsId: "8cf687db-fdd1-450d-ab75-2aedbe5aec66",
                                             url          : "http://10.60.156.11/etc-2.0/etc-deployment-v2" +".git"]]
    ]
    sleep(5)
    def folderPreDeploy= sh(script: 'pwd', returnStdout: true)
    env.buildFolderPreDeployResult = folderPreDeploy.trim()
    try {
        sh """
            pwd
            cd k8s/etc/backend
            ls -la
            sh update-version.sh ${version} ${service}-pre-deployment
            kubectl -n ${namespace} apply -f ${service}-pre-deployment* --kubeconfig=${configFile}
            kubectl -n ${namespace} apply -f ${service}-service* --kubeconfig=${configFile}
            sleep 120
        """
        dir("${env.buildFolderPreDeployResult}/k8s/etc/backend"){
            echo "Get Pods, service detail"
            sh """
            kubectl -n ${namespace} get pods,svc --kubeconfig=${configFile}
            """
            def checkProcessRunning = sh(returnStdout: true, script: "kubectl -n etc --kubeconfig='${configFile}' get pods --sort-by=.status.startTime | grep '${service}' | tail -n 1 | awk '{print \$3}'").trim()
            echo "checkProcessRunning: $checkProcessRunning ${service}"

            if(checkProcessRunning == "Running") {
                env.PRE_RELEASE_PORT = sh(returnStdout: true, script: "kubectl -n ${namespace} --kubeconfig='${configFile}' get svc | grep '${service}' | awk '{print \$5}' | grep -o '[[:digit:]]*' | tail -n 1").trim()
                echo "port: $env.PRE_RELEASE_PORT"
                env.PRE_RELEASE_IP = sh(returnStdout: true, script: "kubectl -n ${namespace} --kubeconfig='${configFile}' get node -o wide | head -2 | tail -1 | awk '{print \$6'}").trim()
                echo "ip: $env.PRE_RELEASE_IP"
            } else {
                error "Deploy service ${service} version ${version} to k8s ${enviroment} failure open port $env.STAGING_PORT"
            }
        }   
    }catch(err){
        error "Deploy to k8s failure"
    }
}
def autoTestIDC(){
    if(env.automations_test_idc == ""){
        echo "skip automations Test"
    } else {
        // sleep(60)
        node("$env.nodeRunTest"){
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
                                                                    url          : "${env.automations_test_idc}"]]
                ]
                bat """
                    mvn clean verify -Dtags="features:upcode"
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
                reportTitles         : 'ETC-DMDC-IDC'])
                def testResultComment = "<b>Functional Test Result:</b> <br/><br/>" +
                        "<i><a href='${env.BUILD_URL}HTML_20Report/'>Details Funcational Test Report...</a></i><br/><br/>"
                env.FUNCTIONAL_TEST_RESULT_STR = testResultComment
            }
        }
    }
}
def rollBackTag() {
    stage("1. Get ENV Productions To Rollback"){
        sh '''
            pwd
            mkdir config-file
            cd config-file
        '''
        dir('config-file'){
            checkout changelog: true, poll: true, scm: [
                $class                           :  'GitSCM',
                branches                         : [[name: "master"]],
                doGenerateSubmoduleConfigurations: false,
                extensions                       : [[$class: 'UserIdentity',
                                                    email : 'hienptt22@viettel.com.vn', name: 'hienptt22'],
                                                    [$class: 'CleanBeforeCheckout']],
                submoduleCfg                     : [],
                userRemoteConfigs                : [[credentialsId: "63265de3-8396-40f9-803e-5cd0b694e519",
                                                    url          : "${env.config_productions}"]]
            ]
        }
        sleep(5)
        sh '''
            ls -la
            cp config-file/production-config.yml .
            rm -rf config-file
            cat production-config.yml
        '''
    }
    def rollbacker = ""
    def config = readYaml file: "production-config.yml"
    env.rollback_list = config['rollback_list']
    env.ip_productions = config['ip_productions']
    echo "Deploy List : ${env.deployer_list}"
    echo "IP Productions : ${env.ip_productions}"
    def versionRollBack = ''
    env.DEPLOY_RESULT_DESCRIPTION += "<h4>Test & Verify Phase Result</h4>"
    stage('Wait for user submit Version to rollback') {
        try {
            rollbacker = env.rollback_list
            echo "rollbacker: ${rollbacker}"
            timeout(time: 24, unit: 'HOURS') {
                versionRollBack = input(
                    submitter: "${rollbacker}",
                    submitterParameter: 'submitter',
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
    env.GIT_TAG_ROLLBACK = versionRollBack.Version
    echo "Version: ${env.GIT_TAG_ROLLBACK}"
    // def statusCode = sh(script: "git show-ref --verify refs/tags/${env.GIT_TAG_ROLLBACK}", returnStatus: true)
    env.GIT_TAG_ROLLBACK = versionRollBack['Version']
    echo "Version: $env.GIT_TAG_ROLLBACK"
   
        if(rollbacker.contains(versionRollBack.submitter)){
            stage('4. Deploy to Productions'){
                echo "Tag: ${env.GIT_TAG_ROLLBACK}"
                jenkinsfile_CI.release2k8s('config_155_160','kafka-deployment_155_160.yml',"${env.GIT_TAG_ROLLBACK}")
            }
            stage("5. Automations Testing after upcode"){
                jenkinsfile_CI.autoTest("${env.ip_productions}","${env.tagsTestUpcode}")
            }
            currentBuild.result = "SUCCESS"
        }else {
            stage("Cancel deploy process") {
                echo "Version: $env.GIT_TAG_ROLLBACK"
                echo "Deploy process is canceled."
                currentBuild.result = "ABORTED"
            }
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
    rollBackTag       : this.&rollBackTag,
    deployToPreRelease: this.&deployToPreRelease,
    deployToProductionVTNet: this.&deployToProductionVTNet
]

