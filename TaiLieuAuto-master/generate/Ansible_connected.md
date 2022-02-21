# Ansible Connected

`Hướng dẫn cấu hình để kết nối Ansible tới Server`

## 1. Yêu cấu đối với server test

### a. Đối với windows

- PS version `>= 3.0`

### b. Đối với server centos

- Cần có python vì ansible connect sử dụng `python`

## 2. Config kết nối

### a. Đối với server test centos

- Bước 1: Config host trong file `/etc/ansible/host` gồm:
  - `Ví dụ`: 10.60.158.34 ansible_user=amione
- Bước 2: Copy key ssh public tới server test

```shell
  cat ~/.ssh/id_rsa.pub | ssh amione@10.60.158.33 'cat >> ~/.ssh/authorized_keys'
```

`Lưu ý`: Trong trường hợp `chưa có` file authorized_keys ==> ssh vào server và `tạo file`. `Cần` phải chạy những lệnh sau nếu chưa có .ssh. Run command

```shell
    ssh-keygen
    cd ~/.ssh
    touch authorized_keys
```

`Fix lỗi Permission publickey` bằng cách run lệnh sau:
change `mode` folder `.ssh` và file `authorized_keys`:

```shell
   chmod -R 700 .ssh && chmod -R 600 authorized_keys
```

### b. Đối với server test là windows

- Bước 1: Config host trong file `/etc/ansible/host` gồm:

  `Ví dụ`:

```shell
10.60.156.203 ansible_user=user ansible_password=password ansible_port=10000 ansible_connection=winrm ansible_winrm_server_cert_validation=ignore
```

`Có thể tạo file yml để config:`

```shell
 ansible_user: user
	ansible_password: password
	ansible_port: 10000
	ansible_connection: winrm
	ansible_winrm_server_cert_validation: ignore
	ansible_winrm_transport: basic
	ansible_winrm_operation_timeout_sec: 60
	ansible_winrm_read_timeout_sec: 70

```

- Bước 2: Cấu hình winrm trên windows để connect tới ansible


    1. Copy file ansible-dev.zip tới server test
    2. Run powershell với Adminitrator các command sau:

```shell
	$file = "C:\Users\app\Desktop\ansible-devel\ansible-devel\examples\scripts\ConfigureRemotingForAnsible.ps1" //đường dẫn tới file ConfigureRemotingForAnsible.ps1
	powershell.exe -ExecutionPolicy ByPass -File $file -Verbose
```

    3. Change port winrm

```shell
	winrm set winrm/config/Listener?Address=*+Transport=HTTP '@{Port="9999"}'
	winrm set winrm/config/Listener?Address=*+Transport=HTTPS '@{Port="10000"}'
```

    4. Config TrustedHosts:

```shell
	winrm set winrm/config/client '@{TrustedHosts="10.60.156.96,10.60.156.43"}'
```

`Lưu ý`: Do \$PSVersion yêu cầu `>=3.0`. Nếu thấp hơn cần upgrade
`Nếu cần` Thực hiện các lệnh sau:

```shell
	$file = "C:\Users\test\Desktop\ansible-devel\ansible-devel\examples\scripts\upgrade_to_ps3.ps1"
```

`Note có thể dùng hoặc không`

```shell
	(Get-PSSessionConfiguration -Name Microsoft.PowerShell).Permission
	Set-PSSessionConfiguration -Name Microsoft.PowerShell -showSecurityDescriptorUI
	(Get-PSSessionConfiguration -Name "Microsoft.PowerShell").SecurityDescriptorSDDL
	$SDDL = " "
	Set-PSSessionConfiguration -Name Microsoft.PowerShell -SecurityDescriptorSddl $SDDL
	winrm set winrm/config/service @{AllowUnencrypted="true"}
	winrm set winrm/config/Client '@{AllowUnencrypted = "true"}'
```

`Config permission for windows`

```shell
	winrm configSDDL default
	add permission read and execute
```

copy to group Admin run

## 3. Check connect

- Đối với windows: `ansible ip -m win_ping`
- Đối với centos: `ansible ip -m ping`

## 4. Run ansible-playbook

- Windows: Run command

```shell
	#Truyền biến vào file
	sudo ansible-playbook file.yml VERSION=?
	#trong trường hợp truyền nhiều biến vào file ==> sử dụng mode -e
	sudo ansible-playbook file.yml -e VERSION=? -e VERSION1=?
```

- Centos:

```shell
	ansible-playbook file.yml
```

`Lưu ý`: Mode debug check lỗi khi run ansible:

```shell
	ansible-playbook file.yml -vvvv
```

## 5. Ansible requies server powershell 4.0 with windows server 2008 R2

- Để có thể setup yêu cầu update to `Service package 1 - SP1`
- add feature powershell PSI
- Setup `.Net 4.5`
- update to powershell `4.0`

## 6. Credential reject

- Run

```
winrm enumerate winrm/config/listener
Set-Item -Path WSMan:\localhost\Service\AllowUnencrypted -Value $true
winrm quickconfig -transport:https
winrm create winrm/config/Listener?Address=*+Transport=HTTPS @{Hostname="WIN-T3GLPIK271H"; CertificateThumbprint="E99FF3B294B3294E3AA1F7D1B0D099E95E6FD8F4"}
Enter-PSSession -Cn vc01 -UseSSL
Enter-PSSession -Cn vc01.winadmin.org -UseSSL
set ssl:verify-certificate no
set ssl:check-hostname no
```
