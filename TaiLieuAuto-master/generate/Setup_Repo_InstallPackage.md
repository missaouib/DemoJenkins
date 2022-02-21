# `Cấu hình yum repo install via repo nội bộ`

`Một số cấu hình cần thiết khi sử dụng Docker`

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
