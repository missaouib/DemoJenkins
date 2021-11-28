# Installing dependencies

## Installing and configuring PostgreSQL:

Install the PostgreSQL version included in your version of Ubuntu:
````
sudo apt-get install postgresql
````

After PostgreSQL is installed, create the PostgreSQL database and user:
````
sudo -i -u postgres psql -c "CREATE DATABASE onlyoffice;"
sudo -i -u postgres psql -c "CREATE USER onlyoffice WITH password 'onlyoffice';"
sudo -i -u postgres psql -c "GRANT ALL privileges ON DATABASE onlyoffice TO onlyoffice;"
````
## Installing rabbitmq:
````
sudo apt-get install rabbitmq-server
````