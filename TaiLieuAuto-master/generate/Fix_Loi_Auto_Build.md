# FIX LỖI TRIỂN KHAI AUTO BUILD & AUTO DEPLOY

`Hướng dẫn fix một số lỗi xảy ra khi thực hiện AUTO BUILD và AUTO DEPLOY trên Jenkins`

## 1. Hướng dẫn bước sau khi install java

- Thực hiện run lệnh sau:

```shell
  update-alternatives --install /usr/bin/java java /home/app/server/jdk1.8.0_171/bin/java 0
  update-alternatives --config java
```

## 2. Hướng dẫn fix lỗi khi triển khai Auto Build

### `Lỗi chung`

#### 2.1. Version java

- Lỗi :

```shell
Exception in thread "main" java.lang.UnsupportedClassVersionError: org/apache/tools/ant/launch/Launcher : Unsupported major.minor version 52.0
	at java.lang.ClassLoader.defineClass1(Native Method)
	at java.lang.ClassLoader.defineClass(ClassLoader.java:800)
	at java.security.SecureClassLoader.defineClass(SecureClassLoader.java:142)
	at java.net.URLClassLoader.defineClass(URLClassLoader.java:449)
	at java.net.URLClassLoader.access$100(URLClassLoader.java:71)
	at java.net.URLClassLoader$1.run(URLClassLoader.java:361)
	at java.net.URLClassLoader$1.run(URLClassLoader.java:355)
	at java.security.AccessController.doPrivileged(Native Method)
	at java.net.URLClassLoader.findClass(URLClassLoader.java:354)
	at java.lang.ClassLoader.loadClass(ClassLoader.java:425)
	at sun.misc.Launcher$AppClassLoader.loadClass(Launcher.java:308)
	at java.lang.ClassLoader.loadClass(ClassLoader.java:358)
	at sun.launcher.LauncherHelper.checkAndLoadMain(LauncherHelper.java:482)
Build step 'Invoke Ant' marked build as failure
```

- Nguyên nhân: Do sử dụng `verion java` không phù hợp

- Fix: `Không` cấu hình `JDK system` xuống `java 1.7`. Chỉ cấu hình java version ở mục Build Trigger và Build.

#### 2.2. Version javadoc

- Lỗi:

```shell
	-do-compile:
		[mkdir] Created dir: /u01/jenkins/workspace/mPBX_webservice/mPBXService/WSmPBX/build/empty
		[mkdir] Created dir: /u01/jenkins/workspace/mPBX_webservice/mPBXService/WSmPBX/build/generated-sources/ap-source-output
		[javac] Compiling 141 source files to /u01/jenkins/workspace/mPBX_webservice/mPBXService/WSmPBX/build/web/WEB-INF/classes
		[javac] javac: invalid target release: 1.8
		[javac] Usage: javac <options> <source files>
		[javac] use -help for a list of possible options
```

- Nguyên nhân: `Javadoc` sử dụng là `1.8` nhưng build với `javadoc là 1.7`

- Fix: Sử dụng `java 1.8` để Build


    ``====> Sau đó gặp lỗi như bên dưới.``

#### 2.3. Version java doctype

- Lỗi:

```shell
	[javadoc] Constructing Javadoc information...
	[javadoc] Standard Doclet version 1.8.0_171
	[javadoc] Building tree for all the packages and classes...
	[javadoc] /u01/jenkins/workspace/mPBX_webservice/mPBXService/WSmPBX/src/java/com/viettel/ipcc/beans/MpbxSubInfo.java:164: warning: no description for @return
	[javadoc]      * @return
	[javadoc]        ^
	[javadoc] /u01/jenkins/workspace/mPBX_webservice/mPBXService/WSmPBX/src/java/com/viettel/utils/DataUtils.java:499: warning: no description for @param
	[javadoc]      * @param lstPhone
	[javadoc]        ^
	[javadoc] /u01/jenkins/workspace/mPBX_webservice/mPBXService/WSmPBX/src/java/com/viettel/utils/DataUtils.java:500: warning: no description for @return
	[javadoc]      * @return
	[javadoc]        ^
	[javadoc] /u01/jenkins/workspace/mPBX_webservice/mPBXService/WSmPBX/src/java/com/viettel/utils/DataUtils.java:540: warning: no description for @param
	[javadoc]      * @param lstCode
	[javadoc]        ^
	[javadoc] /u01/jenkins/workspace/mPBX_webservice/mPBXService/WSmPBX/src/java/com/viettel/utils/DataUtils.java:541: warning: no description for @param
	[javadoc]      * @param businessCode
	[javadoc]        ^
	[javadoc] /u01/jenkins/workspace/mPBX_webservice/mPBXService/WSmPBX/src/java/com/viettel/utils/DataUtils.java:542: warning: no description for @return
	[javadoc]      * @return
	[javadoc]        ^
```

- Nguyên nhân: Project `sử dụng java 1.7` nhưng `build với java 1.8`

- Fix: Cấu hình java version trong phần `Build Trigger và Build về 1.7`

#### 2.4. Version java trên server

- Lỗi: Không `start được service`

- Nguyên nhân: Sử dụng `java 1.8` nhưng không `update-alternatives --config java` trên server với path tới `java 1.8`

- Fix: `update-alternatives` cho java

## 3. project .NET

### 3.1. Project publish result:

- Phải có option: /p:DeployOnBuild=true

### 3.2. Hướng dẫn 7zip một số file result to server deploy

### 3.3. Một số project dùng Dotnet để build

- Run command sau:

````shell
del "%WORKSPACE%\1. Web\SMAS.Web\obj\Release\Package\PackageTmp\bin\Oracle.DataAccess.dll"  "%WORKSPACE%\1. Web\SMAS.Web\obj\Release\Package\PackageTmp\Web.config" "%WORKSPACE%\1. Web\SMAS.Web\obj\Release\Package\PackageTmp\bin\System.Data.SQLite.dll"
 ```

### 3.4. Lỗi add thêm đường dẫn file config

- Lỗi:

```shell
D:\VS2017\MSBuild\Microsoft\VisualStudio\v15.0\Web\Microsoft.Web.Publishing.targets(2311,5): error : Could not open Source file: Could not find a part of the path 'D:\Jenkins\workspace\TCQG_BUILD\TCMR_Web\WebReport\Views\Web.config;Views\Web.config'.
[D:\Jenkins\workspace\TCQG_BUILD\TCMR_Web\WebReport\WebReport.csproj]
````

- Fix

  `Thêm param to build:`

```shell
	/p:AutoParameterizationWebConfigConnectionStrings=False
```

## 4. project Ant

### 4.1. Lỗi javadoc build ant với java 1.8

- Fix:

`Thêm` vào trong file `build-iml.xml` như sau:

```shell
  <arg value="-Xdoclint:none"/> vào <javadoc> </javadoc>
```

### 4.2. Lỗi thiếu lib, không có file nbproject trong source.

- Adđ lib tới source để tạo folder `nbproject`

## 5. Lỗi chạy Ansible

### 5.1. Start tomcat

- Phải `start tomcat` với `nohup`

### 5.2. Run java -jar

- Có thể chạy lệnh shell để start

```shell
	shell: |
      cd
	  nohup java -jar ..... &
```

## 6. Lỗi pipeline

- Nếu sử dụng `if else` phải đặt trong `script`

- pipeline trong `stages{ stage { steps{}}}` --> phải có `steps`

## 7. build gradle

- gradle wrapper

- ./gradlew clean

- ./gradlew assemble

- gradle build

## 8. Lỗi build java maven

- Lỗi:

```shell
	error: diamond operator is not supported in -source 1.5
	[ERROR] (use -source 7 or higher to enable diamond operator)
```

- Fix như sau:

  Thêm vào phần `config source version` sử dụng:

```shell
	Goals and options: -Dmaven.compiler.source=1.8 -Dmaven.compiler.target=1.8
```
