# Hướng dẫn cài đặt và sử dụng gitlab-ci

`Hướng dẫn cài đặt gitlab runner để sử dụng CI/CD`

## `Yêu cầu cấu hình`

Đối với server centos yêu cầu như sau:

### Git2u

Cài đặt như sau:

```shell
	sudo yum -y install  https://centos7.iuscommunity.org/ius-release.rpm
	sudo yum install git2u
	git --version
	git version 2.16.6
```

## Cài đặt như sau

### Bước 1: Download package về máy

    - name: gitlab-runner_amd64.rpm

### Bước 2: Run command

```shell
	rpm -i gitlab-runner_amd64.rpm
```

### Bước 3: Cấu hình gitlab-runner register

```shell
	sudo gitlab-runner register
```

    - Lấy thông tin đầu vào tại

![GitLab runner](./gitlab-runner-info.png) - Nhập tag - Nhập command: shelll

### Bước 4: Start runner

```shell
	sudo gitlab-runner start
```

### Bước 5: Check runner đã avalable trên git hay chưa

## Lưu ý

Mặc định sau khi cài gói gitlab-runner sẽ chạy với user gitlab-runner
Do vậy với user bình thường cấp để ssh vào server thì có thể không check được source code
khi build job. Để thay đổi user chạy và thư mục làm việc của gitlab-runner có thể
sửa trong file `/etc/systemd/system/gitlab-runner.service` với nội dung như sau:

```shell
[Unit]
Description=GitLab Runner
After=syslog.target network.target
ConditionFileIsExecutable=/usr/lib/gitlab-runner/gitlab-runner

[Service]
StartLimitInterval=5
StartLimitBurst=10
ExecStart=/usr/lib/gitlab-runner/gitlab-runner "run" "--working-directory" "/home/app/gitlab-runner" "--config" "/etc/gitlab-runner/config.toml" "--service" "gitlab-runner" "--syslog" "--user" "app"

Restart=always
RestartSec=120

[Install]
WantedBy=multi-user.target
```
