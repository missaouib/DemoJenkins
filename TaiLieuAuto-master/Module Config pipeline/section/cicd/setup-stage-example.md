# Tài liệu hướng dẫn tích hợp thêm các stage trên luồng CI/CD

Trong phần này sẽ hướng dẫn chi tiết cách để tích hợp thêm các stage
trong luồng triển khai CI/CD bao gồm:
- Job CI bao gồm các stage sau: sonarQuebe, Unittest, build, deploy,
automations test, performance test, security test, tích hợp quản lý version
database. ==> Các stage này cơ bản đã đủ các stage trong luồng CI của level 4.
- Job CD cơ bản sẽ bao gồm các stage như sau: phân quyền deploy, lấy version ứng dụng
deploy lên server, update database, run automations test.

