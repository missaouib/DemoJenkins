# Một số ví dụ

## genChangelog

  ```shell
  liquibase --changeLogFile=changelog\genChangelog\sql\db_telecare_%schema%_generateChangeLog_%currDate%.sql
  liquibase --diffTypes="data" --changeLogFile=changelog\sql\genChangelog\db_telecare_%schema%_Data_generateChangeLog_%currDate%.mariadb.sql  generateChangeLog

  ```
## Snapshot

```shell
call liquibase snapshot --snapshotFormat=json > snapshot\snapshot_%currDate%.json
```

## changeLogSync

```shell
    liquibase --driver=org.mariadb.jdbc.Driver --classpath=drivers/mariadb-java-client-2.4.0.jar --defaultSchemaName=telecare --url=jdbc:mariadb://10.60.157.110:3306/telecare --username={{user}} --password={{password}} --changeLogFile=telecare/changelog-master.xml changeLogSyncSQL
 ```

## changeLogSync

```shell
    liquibase --driver=org.mariadb.jdbc.Driver --classpath=drivers/mariadb-java-client-2.4.0.jar --defaultSchemaName=telecare --url=jdbc:mariadb://10.60.157.110:3306/telecare --username={{user}} --password={{password}} --changeLogFile=telecare/changelog-master.xml changeLogSync
 ```
## updateSQL

```shell
liquibase --driver=org.mariadb.jdbc.Driver --classpath=drivers/mariadb-java-client-2.4.0.jar --changeLogFile=changelog-master.xml --url="jdbc:mariadb://10.60.157.110:3306/telecare" --username={{user}} --password={{pass}} updateSQL
```

## update

```shell
liquibase --driver=org.mariadb.jdbc.Driver --classpath=drivers/mariadb-java-client-2.4.0.jar --changeLogFile=changelog-master.xml --url="jdbc:mariadb://10.60.157.110:3306/telecare" --username={{user}} --password={{pass}} update
```
## diff

```shell
liquibase --driver=org.mariadb.jdbc.Driver --classpath=drivers/mariadb-java-client-2.4.0.jar --defaultSchemaName=telecare --referenceUrl=jdbc:mariadb://10.60.157.110:3306/telecare --referenceUsername=telecare --referencePassword=123456aA@ --outputFile=telecare\diffLog\diff_%currDate%.txt --url=offline:mariadb?snapshot=telecare\snapshot\%newest% diff
```

## diffChangeLog

```shell
liquibase --driver=org.mariadb.jdbc.Driver --classpath=drivers/mariadb-java-client-2.4.0.jar --defaultSchemaName=telecare --referenceUrl=jdbc:mariadb://10.60.157.110:3306/telecare --referenceUsername=telecare --referencePassword=123456aA@ --url=offline:mariadb?snapshot=telecare\snapshot\%newest% --changeLogFile=telecare\changelog\changelog_%currDate%.mariadb.sql diffChangeLog
```

## tag

```shell
liquibase --driver=org.mariadb.jdbc.Driver --classpath=drivers/mariadb-java-client-2.4.0.jar --changeLogFile=changelog-master.xml --url="jdbc:mariadb://10.60.157.110:3306/telecare" --username={{user}} --password={{pass}} tag v1.${BUILD_NUMBER}
```

## rollback Tag
  Để có thể sử dụng rollback tag thì trong changeSet cần add manual rollback script. Nếu không script sẽ không 
  chạy được.
```shell
liquibase --driver=org.mariadb.jdbc.Driver --classpath=drivers/mariadb-java-client-2.4.0.jar --changeLogFile=changelog-master.xml --url="jdbc:mariadb://10.60.157.110:3306/telecare" --username={{user}} --password={{pass}} rollback v1.${BUILD_NUMBER}
```
Ví dụ add rollback script vào sql changelog.
```sql
    --liquibase formatted sql

    --changeset hienptt22:create_database_test
    create table test(
        project_id int auto_increment,
        project_name varchar(255) not null,
        begin_date date,
        end_date date,
        cost decimal(15,2) not null,
        created_at timestamp default current_timestamp,
        primary key(project_id)
    );

    --rollback DROP TABLE test
```

## Sử dụng defaultsFile

```shell
liquibase --outputFile=diffLog\diff_%currDate%.txt --defaultsFile="liquibase-dev.properties" diff
```
