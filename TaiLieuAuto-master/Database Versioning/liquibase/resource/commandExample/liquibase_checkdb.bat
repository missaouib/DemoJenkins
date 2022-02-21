echo @off

set hour=%time:~0,2%
if "%hour:~0,1%" == " " set hour=0%hour:~1,1%
echo hour=%hour%

set currDate=%hour%%time:~3,2%%time:~6,2%_%DATE:~10,4%%DATE:~4,2%%DATE:~7,2%
echo %currDate%

pushd snapshot
for /f "tokens=*" %%a in ('dir /b /od') do set newest=%%a
echo "%newest%"
popd


call liquibase --outputFile=diffLog\diff_%currDate%.txt --url=offline:mariadb?snapshot=snapshot\%newest%  diff

call liquibase --url=offline:mariadb?snapshot=snapshot\%newest% --changeLogFile=changelog\changelog_%currDate%.mariadb.sql diffChangeLog

  for /f %%C in ('Find /V /C "" ^< changelog\changelog_%currDate%.mariadb.sql') do set Count=%%C
    echo The file has %Count% lines
    echo changelog\changelog_%currDate%.mariadb.sql
    if %Count% gtr 1 echo F|XCOPY /S /I /Q /Y /F changelog\changelog_%currDate%.mariadb.sql changelog_%currDate%.mariadb.sql && liquibase tag %currDate%
    if %Count% LEQ 1 type changelog\changelog_%currDate%.mariadb.sql && del changelog\changelog_%currDate%.mariadb.sql && echo Deleting changelog_%currDate%.mariadb.sql
 
call liquibase snapshot --snapshotFormat=json > snapshot\snapshot_%currDate%.json





