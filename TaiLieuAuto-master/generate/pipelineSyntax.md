# Một số config thường dùng trong pipeline

`Hướng dẫn viết hàm chạy trong pipeline jenkins`

## 1. Run shell trong pipeline

```shell
   sh "/home/app/server/sonar-scanner/bin/sonar-scanner " +
      "-Dsonar.projectName=${env.SONAR_QUBE_PROJECT_KEY} " +
      "-Dsonar.projectKey=${env.SONAR_QUBE_PROJECT_KEY} " +
      "-Dsonar.java.binaries=. " +
      "-Dsonar.sources=./ " +
      "-Dsonar.exclusions=**/*.zip,**/*.jar,**/*.html,**/build/**,**/target/**,**/.settings/**,**/.mvn/**"
```

`Run nhiều lệnh với biến`

```shell
	sh " sudo ansible-playbook deploy_to_productions_app_47.yml -e VERSION=${version} -e GROUPID=TCQG_CI_Web"
```

## 2. Run bat trong windows với biến

```shell
	bat "7z a -tzip " +
        "${moduleName}.zip " +
		".\\TCMR_Web\\${moduleName}\\obj\\Release\\Package\\PackageTmp\\*"
```

## 3. Upload Artifact to nexus

```shell
	stage('Upload artifact to Nexus server'){
        def uploadSuccessComment = "<b>Build & package Artifact Results - " +
                                      "Build Artifact module ${moduleName} is created. "
            nexusArtifactUploader artifacts: [[artifactId: "${groupId}_${moduleName}", classifier: '', file: "${moduleName}.zip", type: 'zip']], credentialsId: '5a87e1f9-d160-4e56-b06c-4158622898be', groupId: "${groupId}_${moduleName}", nexusUrl: '10.60.156.26:8081', nexusVersion: 'nexus3', protocol: 'http', repository: 'msbuild', version: "1.${BUILD_NUMBER}"
            env.PACKAGE_UPLOAD_IMAGE_RESULT_STR = uploadSuccessComment
    }
```

## 4. Run jacoco trong pipeline

```shell
	jacoco classPattern: 'auth-service/target/classes,invoice-service/target/classes,categories-service/target/classes,kpilog-service/target/classes,media-service/target/classes,notification-service/target/classes,payment-service/target/classes',
           sourcePattern: 'categories-service/src/main/java,auth-service/src/main/java,invoice-service/src/main/java,kpilog-service/src/main/java,media-service/src/main/java,notification-service/src/main/java,payment-service/src/main/java'
```

## 5. Publish HTML report

```shell
	publishHTML([
                allowMissing         : false,
                alwaysLinkToLastBuild: false,
                keepAll              : true,
                reportDir            : 'cicd/target/jacoco-aggregate-report/',
                reportFiles          : 'index.html',
                reportName           : 'Code-Coverage-Report',
                reportTitles         : 'Code-Coverage-Report'])
```

## 6. Run job đã có trên jenkins with sleep

```shell
	stage('Automations Test in Staging'){
        // sleep(120)
        // build job: 'Autotest_TiemChung'
        echo 'Test function'
    }
```

## 7. Publish unit test in pipeline

```shell
	junit '*/target/*-reports/TEST-*.xml'
```

## 8. Fix lỗi scss thinghub

```shell
	sh(returnStatus: true, script: '''chmod +x rebuild.sh
                bash rebuild.sh
                sed -n 490,500p node_modules/bootstrap/scss/_variables.scss
                mvn clean install -DskipTests=true''')
```

## 9. Chạy ansible trong pipeline để deploy ứng dụng

### 9.1. Đối với server test là linux

`Chạy ansible không sử dụng biến`

```shell
	sh ' ansible-playbook deploy.yml '
```

`Chạy ansible sử dụng biến`

```shell

	sh """
		ansible-playbook deploy.yml -e VERSION=${BUILD_NUMBER}
	"""
```

### 9.2. Đối với server test là Windows

`Chạy ansible không sử dụng biến`

```shell
	sh ' sudo ansible-playbook deploy.yml '
```

`Chạy ansible sử dụng biến`

```shell

	sh """
		sudo ansible-playbook deploy.yml -e VERSION=${BUILD_NUMBER}
	"""
```
