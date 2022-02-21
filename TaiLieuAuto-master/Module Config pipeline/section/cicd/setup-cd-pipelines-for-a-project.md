# Tài liệu hướng dẫn thiết lập các luồng xử lý triển khai liên tục - CD pipelines cho một dự án phần mềm

Trong phần này sẽ trình bày các bước để thiết lập một
luồng CD hoàn chỉnh, xuyên suốt phần này, các stage của
một luồng CD sẽ được viết theo trình tự thực hiện trong một luồng CD.

## Thiết lập luồng CD

### Stage Checkout Source Code

Ở stage này, sau khi bắt được event đánh tag để release
version trên gitlab, jenkins sẽ thực hiện lấy source code
trên gitlab về để chạy các script.

```groovy
    stage("1. Checkout Source Code") {
        jenkinsfile_utils.checkoutSourceCode("PUSH")
        def commitIdStdOut = sh(script: 'git rev-parse HEAD', returnStdout: true)
        env.DEPLOY_GIT_COMMIT_ID = commitIdStdOut.trim()
    }
```

### Stage Get ENV Productions

Stage này thực hiện lấy thông tin cấu hình cho productions
để kiểm tra quyền của user tác động đối với hệ thống.

```groovy
    stage("2. Get ENV Productions"){
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
```

### Stage Wait for maintainer accept or reject to deploy to production

Stage này cấu hình thời gian và user có quyền tác động đến hệ thống.
Xác định input là `Deploy` hay `Abort`

```groovy
def deployInput = "Deploy"
    def deployer = ""
    def config = readYaml file: "production-config.yml"
    env.deployer_list = config['deployer_list']
    env.ip_productions = config['ip_productions']
    echo "Deploy List : ${env.deployer_list}"
    echo "IP Productions : ${env.ip_productions}"
    stage("3. Wait for maintainer accept or reject to deploy to production") {
        try {
            deployer = env.deployer_list
            echo "project_maintainer_list: ${env.project_maintainer_list}"
            echo "deployer: ${deployer}"
            timeout(time: 24, unit: 'HOURS') {
                deployInput = input(
                    submitter: "${deployer}",
                    submitterParameter: 'submitter',
                    message: 'Pause for wait maintainer selection', ok: "Execute", parameters: [
                    choice(choices: ['Deploy', 'Abort'], description: 'Deploy to production or abort deploy process?', name: 'DEPLOY_CHOICES')
                ])
            }
            echo "submitter: ${deployInput.submitter}"
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
```

### Stage Deploy to Productions

Trong trường hợp user nhấn button `Deploy` là user tác động hệ thống
thì sẽ thực hiện deploy tới môi trường `productions`.

```groovy
    if (deployInput.DEPLOY_CHOICES == "Deploy" && deployer.contains(deployInput.submitter)) {
            stage('4. Deploy to Productions'){
                echo "Tag: ${env.config_git_branch}"
                untagVersionDeploy('auth',"${env.config_git_branch}")
                untagVersionDeploy('ms-device',"${env.config_git_branch}")
                untagVersionDeploy('ms-user-manage',"${env.config_git_branch}")
                untagVersionDeploy('ms-app-manage',"${env.config_git_branch}")
                untagVersionDeploy('service-discovery',"${env.config_git_branch}")
                untagVersionDeploy('gateway',"${env.config_git_branch}")
                jenkinsfile_CI.release2k8s('config_155_160','kafka-deployment_155_160.yml',"${env.config_git_branch}")
            }

```

### Stage Automations Testing after upcode

Stage này thực hiện tự động kiểm tra lại luồng chính của ứng dụng để
đảm bảo việc upcode tính năng mới không ảnh hưởng tới tính năng cũ.
(Hoặc nếu tester/dev đã viết code test tính năng mới thì kết quả test
sẽ kiểm tra toàn bộ các case test).

```groovy
    stage("5. Automations Testing after upcode"){
                jenkinsfile_CI.autoTest("${env.ip_productions}","${env.tagsTestUpcode}")
            }
```

## Cơ chế deploy theo từng service

Trong dự án `**thinghub**` hiện nay đang để code của các service để chung
cùng một repository để tiện quản lí. Tuy nhiên việc này lại gặp phải vấn đề
là khi chỉ có code của một vài service thay đổi, luồng CD lại phải build,
đóng gói, deploy lại toàn bộ các service làm tăng thời gian triển khai,
gây rủi ro upcode lỗi cho các service vốn dĩ không có gì thay đổi.

Để giải quyết vấn đề này, luồng `CI staging` và `CD` nên bổ sung thêm
cơ chế phát hiện được những service có sự thay đổi cả từ source code
để chỉ triển khai đúng những service đó. Các đội dự án có thể tham khảo cách này.
