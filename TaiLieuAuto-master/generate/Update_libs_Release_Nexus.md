# `Hướng dẫn update lib Release trên Repository Nexus`

`Hướng dẫn update lib Release trên Repository Nexus để triển khai CI/CD`

## Hướng dẫn các bước thực hiện

- Bước 1: Vào server Nexus tìm kiếm theo groupId lib cần update --> Thực hiện xóa lib trên server nexus
  - Việc này có thể xóa tay hoặc tạo script để xóa tự động trong luồng tích hợp CI/CD nếu lib up lên server là lib
    dùng chung cho nhiều service và đang triển khai CI/CD cho module lib này.
- Bước 2: ssh tới 2 server slave jenkins và xóa folder lib update trong .m2
- Bước 3: Upload lib tới server Nexus nội bộ
  - Công việc này có thể upload bằng tay hoặc thực hiện tự động với các cách sau:
    - Nếu có source code của thư viện thực hiện như hướng dẫn tại [link](./Update_libs_Release_Nexus.md#hướng-dẫn-deploy-lib-khi-có-source-code-của-lib)
    - Nếu chỉ có file jar thực hiện như hướng dẫn tại [link](./Update_libs_Release_Nexus.md#hướng-dẫn-deploy-lib-khi-chỉ-có-file-thư-viện-của-lib)

### Hướng dẫn deploy lib khi có source code của lib.

`Thực hiện các bước sau:`

#### Cấu hình username/pass Nexus để deploy file tới Nexus.

Cấu hình trong file .setting.xml trong .m2

```shell
	<settings>
	  <servers>
		<server>
		  <id>maven-snapshots</id>
		  <username>${env.NEXUS_USERNAME}</username>
		  <password>${env.NEXUS_PASSS}</password>
		</server>
		<server>
		  <id>maven-releases</id>
		  <username>${env.NEXUS_USERNAME}</username>
		  <password>${env.NEXUS_PASSS}</password>
		</server>
	  </servers>
	   <proxies>
		<mirror>
		   <id>viettel-maven-repository</id>
			<mirrorOf>*</mirrorOf>
			<name>Viettel Maven Repository</name>
			<url>http://10.60.108.23:9001/repository/maven-public/</url>
		</mirror>
	  </mirrors>

	</settings>
```

#### Cấu hình file pom.xml để thực hiện deploy file lên Nexus

- Trong file pom.xml thêm nội dung sau:
  - repository sẽ là link mặc định repo upload lên nexus
    Nếu build bản release thì chỉ upload lên maven-releases và khi deploy phải thực hiện xóa
    lib đã có trên nexus trước khi deploy.
    Nếu build bản snapshot thì có thể upload lên cả 2 repo
  ```shell
  	<distributionManagement>
  		<repository>
  			<id>maven-releases</id>
  			<name>Internal Releases</name>
  			<url>http://10.60.108.23:9001/repository/maven-releases/</url>
  		</repository>
  	   <snapshotRepository>
  			<id>maven-snapshots</id>
  			<name>Internal Snapshot Releases</name>
  			<url>http://10.60.108.23:9001/repository/maven-snapshots/</url>
  		</snapshotRepository>
  	</distributionManagement>
  ```
  - Lệnh chạy deploy file lên nexus:
    - Lưu ý: User/pass login Nexus là link 10.60.108.23:9001
  ```shell
  	mvn clean deploy --settings .settings.xml -Denv.NEXUS_USERNAME=userloginNexus -Denv.NEXUS_PASSS=passloginNexus
  ```

### Hướng dẫn deploy lib khi chỉ có file thư viện của lib.

Lưu ý chỉ dùng để deploy file lib tới `maven-snapshots`, đối với `maven-release` cần
đảm bảo file chưa tồn tại hoặc đã tồn tại ít nhất phải có sự sai khác về `version`

#### Chạy lệnh sau để thực hiện đẩy file lên repo.

Đảm bảo trong thư mục chạy phải có file lib cần đẩy lên. Dưới đây là một ví dụ.

    ```shell
    	mvn deploy:deploy-file \
    	-DgroupId=com.viettel \
    	-DartifactId=EInvoiceCommon \
    	-Dversion=1.0-SNAPSHOT \
    	-DgeneratePom=true \
    	-Dpackaging=jar \
    	-DrepositoryId=maven-snapshots \
    	-Durl=http://10.60.108.23:9001/repository/maven-snapshots/ \
    	-Dfile=EInvoiceCommon-1.0-SNAPSHOT.jar
    ```
