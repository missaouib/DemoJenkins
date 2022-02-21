# Maven hóa ứng dụng Java

- [Maven hóa ứng dụng Java](#maven-h%C3%B3a-%E1%BB%A9ng-d%E1%BB%A5ng-java)
  - [Hiện trạng](#hi%E1%BB%87n-tr%E1%BA%A1ng)
  - [Hướng thay đổi](#h%C6%B0%E1%BB%9Bng-thay-%C4%91%E1%BB%95i)
  - [Thực hiện](#th%E1%BB%B1c-hi%E1%BB%87n)
    - [Công cụ](#c%C3%B4ng-c%E1%BB%A5)
    - [Các bước thực hiện (tổng quan)](#c%C3%A1c-b%C6%B0%E1%BB%9Bc-th%E1%BB%B1c-hi%E1%BB%87n-t%E1%BB%95ng-quan)
    - [Sử dụng tool `mvnize`](#s%E1%BB%AD-d%E1%BB%A5ng-tool-mvnize)
  - [Cấu hình `.m2./settings.xml`](#c%E1%BA%A5u-h%C3%ACnh-m2settingsxml)

## Hiện trạng

Các dự án của trung tâm hiện tại thường lưu trữ cả code kèm theo lib
(thậm chí cả doc và các bản build) trong cùng một kho chứa
(hiện tại là trên IBM RTC hoặc gitlab). Việc này có một điểm lợi là có code thì
sẽ có đủ lib để chạy, tuy nhiên, kích thước của kho chứa
sẽ là rất lớn (có thể lên đến cả GB). Repo lớn sẽ gây khó khăn cho việc
áp dụng CI/CD vì mỗi lần lấy code về rất tốn kém thời gian.

## Hướng thay đổi

Để dễ dàng tiến hành áp dụng CI/CD thì repo chỉ nên chứa code/mã nguồn và một
số cấu hình dành cho CI/CD. Library/Dependency cần được tách riêng và lưu trữ
trên Nexus repo (nội bộ) hoặc pull trực tiếp từ Official Maven Repository
liên quan tương tứng.

Khi đó, repo chứa code sẽ rất gọn nhẹ và dễ dàng cho việc clone/lấy code đầy đủ.

## Thực hiện

### Các bước thực hiện (tổng quan)

1. Đưa riêng các lib đang để chung với code ra ngoài
2. Đẩy các lib do Viettel tự viết lên Nexus repository tại đây[http://10.60.108.23:9001/](http://10.60.108.23:9001/).
   Nếu cần tài khoản, liên hệ hienptt22 để tạo tài khoản.
3. Lib nào là lib public (đã có trên official maven repository) thì tiến hành
   lưu lại `groupId`, `artifactId`, và `version` và đẩy vào file pom.
4. Tạo file `pom.xml` ở thư mục gốc của mã nguồn và import các thông tin lib
   nội bộ của Viettel và lib public.
5. Sau khi có file `pom.xml` vẫn nên thực hiện làm sạch file này bằng tay
   để đảm bảo không còn chứa lib rác, và thừa, conflict.

## Cấu hình `.m2./settings.xml`

Do mạng nội bộ không được thông trực tiếp ra ngoài internet, do đó P.CNSX
có dựng một Maven repository nội bộ vừa đóng vai trò đồng bộ và làm proxy
cho Maven Central tại [http://10.60.108.23:9001](http://10.60.108.23:9001)

Để cho maven (trên máy tính) tải các gói từ repo nội bộ thì cần sửa cấu hình
file `.m2/settings.xml` như sau.

```xml
<settings xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xmlns="http://maven.apache.org/SETTINGS/1.0.0"
  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                      https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <mirrors>
    <mirror>
        <id>viettel-maven-repository</id>
        <mirrorOf>*</mirrorOf>
        <name>Viettel Maven Repository</name>
        <url>http://10.60.108.23:9001/repository/maven-public/</url>
    </mirror>
  </mirrors>
</settings>
```

Trên Linux: `~/.m2/settings.xml`

Trên Windows: `C:\Users\<CurrentUserName>\.m2\settings.xml`

Tham khảo thêm tại: [Maven Settings Reference](https://maven.apache.org/settings.html)
