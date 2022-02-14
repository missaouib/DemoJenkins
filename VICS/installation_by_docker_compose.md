# VICS Server Administration Guide

# Installation by docker-compose

@ todo: explain why use alias

The easiest way to get a fully featured and functional setup is using a docker-compose file. There are too many different possibilities to setup your system, so here are only some examples of what you have to look for.

At first, make sure you have chosen the right base image (fpm or apache) and added features you wanted (see below). In every case, you would want to add a database container and docker volumes to get easy access to your persistent data. When you want to have your server reachable from the internet, adding HTTPS-encryption is mandatory! See below for more information.

Make sure to pass in values for MYSQL_ROOT_PASSWORD and MYSQL_PASSWORD variables before you run this setup.

```
# In docker-compose file may not include version. So the version of docker-compose will follow the version of docker on the machine.
# You can also choose a lower version to match your OS and docker version.

version: '3.8'

volumes:
  vics:
  db:

networks:
  net_vics:

# If you want to download OnlyOffice app in VICS interface behind proxy, you must add HTTP_PROXY and HTTPS_PROXY at services/db/environment.
# And if not, you can download app inside VICS's container like the document.

# Using aliases to easily integrate Onlyoffice with VICS. You need to add it at least in VICS and Onlyoffice.

# Currently, VICS is not compatible with MariaDB version 10.6. Therefore, the document recommends the version 10.5.
# if you want to use version 10.6, fix it as follows:  ___command: --transaction-isolation=READ-COMMITTED --log-bin=ROW --innodb-read-only-compressed=OFF___

services:
  db:
    image: mariadb:10.5
    container_name: db
    restart: always
    command: --transaction-isolation=READ-COMMITTED --binlog-format=ROW
    volumes:
      - db:/var/lib/mysql
    environment:
      - MYSQL_ROOT_PASSWORD=vics
    env_file:
      - db.env
    networks:
      net_vics:
        aliases:
          - db
    
  app:
    image: binhnv56/vics
    container_name: vics
    restart: always
    ports:
      - 8080:80
    links:
      - db
    volumes:
      - vics:/var/www/html
    environment:
      - MYSQL_HOST=db
    env_file
      - db.env
    networks:
      net_vics:
        aliases:
          - vics
    depends_on:
      - db

  phpmyadmin:
    image: phpmyadmin
    container_name: phpmyadmin
    restart: always
    ports:
      - 8082:80
    environment:
      - PMA_HOST=db
    networks:
      net_vics:
        aliases:
          - phpmyadmin

  onlyoffice:
    image: onlyoffice/documentserver
    container_name: onlyoffice
    restart: always
    ports:
      - 8081:80
    networks:
      net_vics:
        aliases:
          - docserver        
```

You also need to create an db.env file to store the necessary information for docker-compose.

```
MYSQL_PASSWOR=vics
MYSQL_DATABASE=vics
MYSQL_USER=vics
```

Then run 
```
docker-compose up -d
```

Now you can access VICS at http://localhost:8080/ from your host system.

# Integrate OnlyOffice app with VICS

## Config Onlyoffice

After completing the installation of VICS in 1 of 3 ways: from source, from docker-compose or from dockerfile, you will integrate OnlyOffice in it for easily using files.

We need a terminal into the docker OnlyOffice's container.

```
docker exec -it <onlyoffice_container_name> bash
```

Edit file /etc/onlyoffice/documentserver/local.json :

```
{
  "services": {
    "CoAuthoring": {
      "sql": {
        "type": "postgres",
        "dbHost": "localhost",
        "dbPort": "5432",
        "dbName": "onlyoffice",
        "dbUser": "onlyoffice",
        "dbPass": "onlyoffice"
      },
      "token": {
        "enable": {
          "request": {
            "inbox": true,
            "outbox": true
          },
          "browser": true
        },
        "inbox": {
          "header": "Authorization"
        },
        "outbox": {
          "header": "Authorization"
        }
      },
      "secret": {
        "inbox": {
          "string": "<secret_key>"
        },
        "outbox": {
          "string": "<secret_key>"
        },
        "session": {
          "string": "<secret_key>"
        }
      }
    }
  },
  "rabbitmq": {
    "url": "amqp://guest:guest@localhost"
  }
}
```

After editing, you need to run the command below to save the changes:

```
supervisorctl restart all
```

## Config VICS

### Change Theme

If you want to change the theme of VICS, first you need to download this theme and put it in the VICS'contaier. In the document, the name of the folder containing theme, which you want to change, is "theme".

```
docker cp <path_to_theme> vics:/var/www/html/themes

```

You need to run the command below to change the theme:

```
docker exec -u www-data -it vics php /var/www/html/occ config:system:set theme --value=theme
```

### Download OnlyOffice app inside VICS'container
You need to download Onlyoffice app inside VICS'container and put it in the custom_apps folder

We need a terminal into the docker VICS's container.

```
docker exec -it vics bash
```

Going into custom_apps folder and download Onlyoffice app

```
cd /var/www/html/custom_apps/
```

#### Download app 

Go to the [Onlyoffice Download Page](https://github.com/ONLYOFFICE/onlyoffice-nextcloud/releases)

```
wget https://github.com/ONLYOFFICE/onlyoffice-nextcloud/archive/refs/tags/v7.0.2.tar.gz
tar xvf v7.0.2.tar.gz
rm *.tar.gz
```

#### Edit config.php
Going into config folder and download Onlyoffice app

```
cd /var/www/html/config/
```

Edit config.php file
```
  array (
    0 => '*',
      ),
```

### Config VICS

```
docker exec -u www-data -it vics php /var/www/html/occ config:system:set updatechecker --value="true"
docker exec -u www-data -it vics php /var/www/html/occ config:system:set knowledgebaseenabled --value="false" --type=boolean
docker exec -u www-data -it vics php /var/www/html/occ config:system:set allow_local_remote_servers --value="true"
```

### Setting app in VICS interface

Access VICS at http://localhost:8080/setting/apps and enable Onlyoffice app

access http://localhost:8080/settings/admin/onlyoffice to configure Onlyoffice like that: 

![alt](./setting.png)

- Document Editing Service address: Address to Onlyoffice, where you forward port in docker-compose
- Secret key (leave blank to disable): which key you setup in local.json
- Document Editing Service address for internal requests from the server: onlyoffice's aliases in docker-compose
- Server address for internal requests from the Document Editing Service: vics's aliases in docker-compose

Press "Save".