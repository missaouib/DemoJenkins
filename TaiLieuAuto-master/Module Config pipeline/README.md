# Kiến thức triển khai CI/CD cho ứng dụng

Repository này chứa các kiến thức liên quan đến các công việc và quá trình cần
làm khi tiến hành triển khai CI/CD cho một dự án.

Dưới đây đề cập một số điểm cần lưu ý khi thực hiện (_bấm vào các link dưới để đọc chi tiết_).

**Lưu ý:** Đây là kiến thức dành cho dev về cách thức triển khai CI/CD cho một ứng dụng. Trong
tài liệu này không đề cập đến các phần việc như dựng môi trường CI/CD.

## Mục lục

- [GitLab - kho lưu trữ code cho dự án](./section/1-gitlab.md)
  - [Tổng quan về git và cách dùng](./section/1-gitlab.md#t%E1%BB%95ng-quan-v%E1%BB%81-git-v%C3%A0-c%C3%A1ch-d%C3%B9ng)
  - [GitLab - sử dụng và các lưu ý](./section/1-gitlab.md#gitlab-s%E1%BB%AD-d%E1%BB%A5ng-v%C3%A0-c%C3%A1c-l%C6%B0u-%C3%BD)
- [Maven - tách riêng dependencies và code](./section/2-maven.md)
  - [Hiện trạng](./section/2-maven.md#hi%E1%BB%87n-tr%E1%BA%A1ng)
  - [Hướng thay đổi](./section/2-maven.md#h%C6%B0%E1%BB%9Bng-thay-%C4%91%E1%BB%95i)
  - [Thực hiện](./section/2-maven.md#th%E1%BB%B1c-hi%E1%BB%87n)
  - [Cấu hình `.m2./settings.xml`](./section/2-maven.md#c%E1%BA%A5u-h%C3%ACnh-m2settingsxml)
- [Tích hợp liên tục và triển khai liên tục - CI/CD System](./section/cicd/ci-cd.md)
  - [Giới thiệu về tích hợp liên tục và triển khai liên tục - CI/CD](./section/cicd/ci-cd.md)
  - [Hướng dẫn thiết lập các luồng xử lý tích hợp liên tục - CI pipelines cho một dự án phần mềm](./section/cicd/setup-ci-pipelines-for-a-project.md)
  - [Hướng dẫn thiết lập các luồng xử lý triển khai liên tục - CD pipelines cho một dự án phần mềm](./section/cicd/setup-cd-pipelines-for-a-project.md)
- [Hướng dẫn cấu hình job jenkins để triển khai CI/CD](./section/cicd/setup-job-example.md)
- [Hướng dẫn thêm stage tích hợp triển khai CI/CD trong jenkinsfile](./section/cicd/setup-stage-example.md)
- Một số lưu ý khi triển khai CI/CD pipeline
  - Liên hệ P.CNSX để cấp `user` jenkins - link ứng dụng: 10.60.156.96:8080
  - Khi triển khai mỗi dự án cần tạo `folder` riêng để triển khai.
  - Việc tạo một job CI pipeline trên jenkins cần `approve` của admin ==> liên hệ đ/c hienptt22 để thực hiện thao tác này.
  - Các cấu hình kết nối, setup môi trường build đặc biệt cần gửi lại thông tin chi tiết cho P.CNSX.
  - [Config file sample](./resource/ci-cd-script-example/configFileGroovy/ci-config)
  - [JenkinsFile sample](./resource/ci-cd-script-example/jenkinsfile_groovy)
  - Update thêm slave để triển khai CI/CD: `slave_43`, `node_cicd`, `slave_116`
  - Update thêm `genSonarQubeProjectKey`, thêm `env.groupName` trong trường hợp các phân hệ có các module giống nhau. Ví dụ, đối với dự án ETC tất cả các phân hệ đều có FrontEnd và Backend đặt tên trên repo gitlab giống nhau, cần thêm `env.groupName` là tên phân hệ như env.groupName="IM" trong `ci-config` file
    ```groovy
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
    ```

- [Functional Testing với Postman](./section/3-Functions-Test-API.md#functional-testing-v%E1%BB%9Bi-postman)
  - [Postman là gì](./section/3-Functions-Test-API.md#postman-l%C3%A0-g%C3%AC)
  - [Testing trong Postman](./section/3-Functions-Test-API.md#testing-trong-postman)
  - [Kiểm thử tự động](./section/3-Functions-Test-API.md#ki%E1%BB%83m-th%E1%BB%AD-t%E1%BB%B1-%C4%91%E1%BB%99ng)
  - [Tài liệu tham khảo](./section/3-Functions-Test-API.md#t%C3%A0i-li%E1%BB%87u-tham-kh%E1%BA%A3o)

