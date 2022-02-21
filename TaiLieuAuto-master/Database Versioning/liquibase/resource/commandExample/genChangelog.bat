echo @off

set hour=%time:~0,2%
if "%hour:~0,1%" == " " set hour=0%hour:~1,1%
echo hour=%hour%

set currDate=%hour%%time:~3,2%%time:~6,2%_%DATE:~10,4%%DATE:~4,2%%DATE:~7,2%
echo %currDate%

set schema=%1
echo %schema%
echo "Start genChangelog sql file"

call liquibase --changeLogFile=changelog\sql\genChangelog\db_telecare_%schema%_generateChangeLog_%currDate%.mariadb.sql  generateChangeLog

echo "Start genChangelog datafile"

call liquibase --diffTypes="data" --changeLogFile=changelog\sql\genChangelog\db_telecare_%schema%_Data_generateChangeLog_%currDate%.mariadb.sql  generateChangeLog

echo "Start genChangelog xml file"

call liquibase --changeLogFile=changelog\xml\genChangelog\db_telecare_%schema%_generateChangeLog_%currDate%.xml  generateChangeLog

echo "create snapshot first once"

call liquibase snapshot --snapshotFormat=json > snapshot\snapshot_%currDate%.json
