# `Docker setup`

`Một số cấu hình cần thiết khi sử dụng Docker`

## Hướng dẫn cài docker offline

Đẩy các package docker lên server. Ví dụ đối với bản docker `19.03.11`
đẩy các file sau: --> tất cả các file đã được zip tới `docker_rpm.zip`
containerd.io-1.2.13-3.2.el7.x86_64.rpm
container-selinux-2.107-3.el7.noarch.rpm
docker-ce-19.03.11-3.el7.x86_64.rpm
docker-ce-cli-19.03.11-3.el7.x86_64.rpm

```shell
    unzip docker_rpm.zip
	cd docker_rpm
	sudo rpm -ivh *.rpm
	sudo service docker start
	sudo systemctl enable docker
	sudo groupadd docker
	sudo usermod -aG docker $USER
	newgrp docker
	docker info
	docker --version
```

## Cấu hình local repo để install package qua repo nội bộ

Thực hiện backup nội dung trong folder `/etc/yum.repos.d/` như sau:

```shell
   cd /etc/yum.repos.d/
   mkdir bk
   mv *.repo bk/
   vi local.repo
```

Sau đó thực hiện cấu hình nội dụng file local.repo như sau để trỏ đến repo yum nội bộ:

```shell
    [cent7_repo]
	name=Cent7 Repo
	baseurl=http://10.60.108.23:9001/repository/yum-all-group/
	enabled=1
	protect=0
	gpgcheck=0
	[fedora_repo]
	name=Fedora repo
	baseurl=http://10.60.108.23:9001/repository/fedora-group/
	enabled=1
	protect=0
	gpgcheck=0

```

    Để cài đặt phần mềm chỉ cần thực hiện lệnh:
    ```shell
       yum clean all
       yum install package
    ```

## Add user tới group để run với user thường

```shell
    sudo groupadd docker
	sudo usermod -aG docker $USER
	newgrp docker
```

## Cấu hình proxy

Với Windows, tiến hành cấu hình trong giao diện. Ngoài ra, cần cấu hình proxy
trong file ví dụ đường dẫn như sau:
`C:\Users\hienptt22\.docker\config.json`

```shell
	{
    "proxies":
    {
        "default":
            {
                "httpProxy":"http://10.61.11.42:3128",
                "httpsProxy":"http://10.61.11.42:3128",
                "noProxy":"10.0.0.0/8,localhost"
            }
    }
}
```

Đối với ubuntu, cấu hình như sau:

```shell
	sudo mkdir -p /etc/systemd/system/docker.service.d
	sudo vi /etc/systemd/system/docker.service.d/proxy.conf
```

Nội dung file như sau:

```shell
[Service]
Environment="HTTP_PROXY=http://10.61.11.42:3128"
Environment="HTTPS_PROXY=http://10.61.11.42:3128"
Environment="NO_PROXY=localhost,127.0.0.0/8,10.60.156.72,10.0.0.0/8"
```

Thực hiện run lệnh để kiểm tra như sau:

```shell
   systemctl daemon-reload
   systemctl show --property Environment docker
   systemctl restart docker
   docker info
```

## Cấu hình registry

Cấu hình trong file daemon.json với 2 registry habor và Nexus.
Với Docker version 1.13.1, build cccb291/1.13.1, cấu hình như sau:

```shell
{ "insecure-registries":["10.60.156.72:5000","10.60.156.26:8181","10.60.156.26:8182","10.60.108.23:8181","10.60.108.23:8182"],
  "registry-mirrors": ["10.60.156.26:8181","10.60.156.26:8182","10.60.108.23:8181","10.60.108.23:8182"],
  "add-registry": ["10.60.156.26:8181"]
}

```

Với Docker version 19.03.11, build 42e35e61f3, cấu hình như sau:

```shell
{
  "insecure-registries":["10.60.156.72:5000","10.60.156.26:8181","10.60.156.26:8182","10.60.108.23:8181","10.60.108.23:8182"],
  "registry-mirrors": ["http://10.60.156.26:8181","http://10.60.156.26:8182","http://10.60.108.23:8181","http://10.60.108.23:8182"]
}

```

## install docker-compose

```shell
   sudo curl -L "https://github.com/docker/compose/releases/download/1.25.4/docker-compose-$(uname -s)-$(uname -m)" /usr/local/bin/docker-compose
   sudo chmod +x /usr/local/bin/docker-compose
   docker-compose version

```

## Push image to Nexus 3

```shell
   docker login 10.60.156.26:8182
   docker tag 38ce1319d01e 10.60.156.26:8182/docker_web:1.0
   docker push 10.60.156.26:8182/docker_web:1.0
```
