# Ansible

`Một số command thường dùng khi sử dụng Ansible`

## `Ansible với Linux`

Một số lệnh ansible trong Linux

### 1. Copy file or folder from server local to server remote

```shell
- name: copy file
  copy:
    src: "path_src/file"
    dest: "path_dest/file"
- name: copy folder
  copy:
    src: "path_scr/folder"
    dest:"path_dest/folder"
- name: copy multiple file
  copy:
      src: "{{ item.src }}"
      dest: "{{ item.dest }}"
    with_items:
      - { src: '/home/app/deploy/a.yml', dest: '/home/app' }
      - { src: '/home/app/deploy/build-impl.xml', dest: '/home/app' }
```

### 2. Copy file from server remote into server local

```shell
  - name: copy file to server remote to workspace jenkins
      fetch:
        src: "path_src/file"
        dest: "path_dest/file"
        flat: yes
```

### 3. Copy file from server remote to server local use pattern

```shell
    - name: pattern file
      find:
        paths: /home/app/fileresults_HDDT/
        patterns: '*.xls'
      register: files_to_copy
    - name: use find to get the files list which you want to copy/fetch
      fetch:
        src: "{{ item.path }}"
        dest: /u02/jenkins/workspace/autoperf_HDDT_test212/resultfiles/
        flat: yes
      with_items: "{{ files_to_copy.files }}"
```

### 4. Copy 1 file to multi directory in server remote

```shell
    - name: copy file
        copy:
            src: /home/app/deploy/a.yml
            dest: "{{ item }}"
        with_items:
            - /home/nexus/hddt/aaa.yml
            - /home/nexus/test/bbb.yml
```

### 5. Run command trong server test để start stop service

```shell
   - name: Run command
      shell: |
        cd path
        nohup java -jar test.jar &
        ./start.sh
        cd path/tomcat/bin
        nohup ./startup.sh
```

### 6. Run kill tiến trình

```shell
   - name: kill process tomcat_autoDeploy
      shell: "ps -ef | grep 'apache-tomcat' | grep -v grep | awk '{print $2}' | xargs -r kill -9"
```

### 7. Xóa file và thư mục trong một thư mục

```shell
   - name: remove all in folder generated
      file:
        path: /home/app/Voffice/tomcat_autoDeploy/webapps/
        state: "{{ item }}"
      with_items:
        - absent
        - directory
```

### 8. Check port to start

```shell
   - name: check port
      shell: |
        cd /home/app/ServerAgent-2.2.1
        flag=`netstat -nap | grep 9000 | grep LISTEN | wc -l`
        if [ $flag -eq 0 ];
        then ./startAgent.sh --udp-port 0 --tcp-port 9000 &
           echo `date`": OK" >> agent_start.log;
        else
           echo `date`": fail, agent is started" >> agent_start.log;
        fi;
```

### 9. backup file trong linux theo ngày tháng năm

```shell
   - name: backup build artifact module core
      shell: |
        cd /u01/dms_one_domain/ZOTT/builds-7/core-8001
        cp dmscore.core.war dmscore.core.war.bk$(date +%Y%m%d%H%M%S)
```

## `Ansible với Winodws`

`Một số lệnh ansible trong Windows`

### 1. Ansible với windows get artifact into server nexus

```shell
    - name: get file build artifact
      win_get_url:
        url:  http://10.60.156.26:8081/repository/msbuild/YTCS_BUILD_SYS_SERVICE/hison.system/1.{{VERSION}}/hison.system-1.{{VERSION}}.zip
        dest: 'E:\TEST AUTOBUILD\'
```

### 2. Unzip file windows

```shell
   - name: unzip file build
      win_unzip:
        src: E:\TEST AUTOBUILD\hison.system-1.{{VERSION}}.zip
        dest: E:\TEST AUTOBUILD\SYS\HISONE_SYSTEM\
```

### 3. Tạo và start website trong IIS

```shell
   - name: deploy app in IIS Server
      win_iis_website:
        name: hison_auto_sys
        state: started
        port: 8188
        ip: 10.60.158.53
        application_pool: HISONE.SYSTEM_DAOTAO
        physical_path: E:\TEST AUTOBUILD\SYS\HISONE_SYSTEM\
```

### 3. Tao folder backup với ngày tháng năm trong windows

```shell
   - name: Create folder backup
      win_shell: |
            $folderName = (Get-Date).tostring("dd-MM-yyyy-hh-mm-ss")
            New-Item -itemType Directory -Path C:\S\Apps\4.1\glash\domains\domain1\backup -Name mtracking_service_api_bk$FolderName
            xcopy C:\SmartMotor\ps\glfish4.1\glasish\domains\din1\applons\mtrervice_api\* C:\SmartMotor\Apps\glash4.1\glaish\dains\ain1\bup\mking_service_api_bk$FolderName\ /s /i /Y
```

### 4. undeploy run command windows

```shell
   - name: undeploy
      win_command: "{{ item }}"
      with_items:
        - cmd.exe /c C:\aa\aa\aa.1\glassfish\bin\asadmin --port xxxx --host localhost -u a --passwordfile C:\\Apps\glassfish4.1\glassfish\bin\.txt undeploy mtracking_service_api
```

### 9. Xóa sub file và sub folder windows

```shell
    - name: clear cache
      win_file:
        path: C:\Smaror\Apps\gish4.1\sfish\dons\domain1\gated\
        state: "{{ item }}"
      with_items:
        - absent
        - directory
```

### 9. Copy file windows

```shell
   - name: copy file war to remote server
    win_copy:
     src: target/mtracking_service_api.war
     dest: C:\SmartMotor\Apps\glassfish4.1\glassfish\domains\domain1\applications\mtracking_service_api.war
  - name: deploy
    win_command: "{{ item }}"
    with_items:
      - cmd.exe /c C:\Smaor\As\glas1\glas\bin\dmin --port xxxx --host localhost -u a --passwordfile C:\Smaror\Apps\gsh4.1\glish\bin\pas.txt deploy C:\Smaor\ps\glsh4.1\gish\mns\dain1\aations\mtrvice_api.war
```

### 10. Backup file windows

```shell
   - name: backup folder windows
    win_zip:
      src: D:\test
      dest: D:\test_bk_{{ lookup('pipe', 'date +%Y%m%d-%H%M') }}.zip
```
