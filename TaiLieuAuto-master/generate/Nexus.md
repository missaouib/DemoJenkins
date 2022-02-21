# `Nexus deploy file snapshot`

`Hướng dẫn deploy file tới nexus maven-snapshots`

## Cấu hình username/pass Nexus để deploy file tới Nexus.

Cấu hình trong file .setting.xml trong .m2

```shell
   <settings>
  <servers>
    <server>
      <id>maven-snapshots</id>
      <username>username</username>
      <password>password</password>
    </server>
  </servers>
</settings>
```

Lưu ý chỉ dùng để deploy file lib tới `maven-snapshots`, đối với `maven-release` cần
đảm bảo file chưa tồn tại hoặc đã tồn tại ít nhất phải có sự sai khác về `version`

## Chạy lệnh sau để thực hiện đẩy file lên repo.

Đảm bảo trong thư mục chạy phải có file lib cần đẩy lên.

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

# `Nexus cấu hình file setting.xml để kéo lib qua repo tập trung`

`Hướng dẫn sửa file setting để trỏ tới lib tập trung`

Thêm đoạn sau vào file setting.xml trong cặp thẻ <mirrors></mirrors>

```shell
	<mirror>
       <id>viettel-maven-repository</id>
        <mirrorOf>*</mirrorOf>
        <name>Viettel Maven Repository</name>
        <url>http://10.60.108.23:9001/repository/maven-public/</url>
    </mirror>
```

# Hướng dẫn cấu hình dự án sử dụng Nuget trỏ đến repo tập trung
`Hướng dẫn sử file NuGet.Config`
```shell
   <?xml version="1.0" encoding="utf-8"?>
  <configuration>
    <packageSources>
      <add key="nuget.org" value="https://api.nuget.org/v3/index.json" protocolVersion="3" />
      <add key="localRepo" value="http://10.60.108.23:9001/repository/nuget-group/" />
    </packageSources>
  </configuration>
```

# Hướng dẫn cấu hình dự án sử dụng NPM trỏ đến repo tập trung
`Hướng dẫn sử file .npmrc`
```shell
  registry=http://10.60.108.23:9001/repository/npm-all/
  #progress=true
  timeout=100000
  prefer-offline=true
```
# Hướng dẫn cấu hình dự án sử dụng Gradle trỏ đến repo tập trung
`Hướng dẫn sử file build.gradle`
```shell
   buildscript {
    repositories {
        # jcenter()
        # google()
        maven {
            url  "http://10.60.108.23:9001/repository/maven-public/"
        }
    }
  }

  allprojects {
      repositories {
          # jcenter()
          # google()
          maven {
              url  "http://10.60.108.23:9001/repository/maven-public/"
          }
      }
  }
```

