# VICS Server Administration Guide

## Installation and server configuration by source

### Prerequisites for manual installation

To install required package, please do following command:

```
sudo apt update
sudo apt install apache2 mariadb-server libapache2-mod-php7.4
sudo apt install php7.4-gd php7.4-mysql php7.4-curl php7.4-mbstring php7.4-intl
sudo apt install php7.4-gmp php7.4-bcmath php-imagick php7.4-xml php7.4-zip
```

Now you need to create a database user and the database itself by using the MySQL command line interface. The database tables will be created by VICS when you login for the first time.

To start the MySQL command line mode use the following command and press the enter key when prompted for a password:

```
sudo /etc/init.d/mysql start
sudo mysql -uroot -p
```

Then a MariaDB [root]> prompt will appear. Now enter the following lines, replacing username and password with appropriate values, and confirm them with the enter key:

```
CREATE USER 'vics'@'localhost' IDENTIFIED BY 'vics';
CREATE DATABASE IF NOT EXISTS vics CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
GRANT ALL PRIVILEGES ON vics.* TO 'vics'@'localhost';
FLUSH PRIVILEGES;
```

You can quit the prompt by entering:

```
quit;
```

Now clone source code from VTS-WORKSPACE'github:

```
git clone https://github.com/VTS-WORKSPACE/server.git
```

If you keep the source code, you will surely get the following error when running:
___Composer autoloader not found, unable to continue. Check the folder "3rdparty". Running "git submodule update --init" will initialize the git submodule that handles the subfolder "3rdparty".___

To fix this error, the best way is before build system, access to the server director VICS and execute the following command:

```
git submodule update --init
```

### Apache Web server configuration

Configuring Apache requires the creation of a single configuration file. On Debian, Ubuntu, and their derivatives, this file will be /etc/apache2/sites-available/vics.conf. On Fedora, CentOS, RHEL, and similar systems, the configuration file will be /etc/httpd/conf.d/vics.conf.

You can choose to install vics in a directory on an existing webserver, for example https://www.example.com/vics/, or in a virtual host if you want vics to be accessible from its own subdomain such as https://cloud.example.com/.

To use the directory-based installation, put the following in your vics.conf replacing the Directory and Alias filepaths with the filepaths appropriate for your system:

```
Alias /vics "/var/www/vics/"

<Directory /var/www/vics/>
  Require all granted
  AllowOverride All
  Options FollowSymLinks MultiViews

  <IfModule mod_dav.c>
    Dav off
  </IfModule>
</Directory>
```

To use the virtual host installation, put the following in your vics.conf replacing ServerName, as well as the DocumentRoot and Directory filepaths with values appropriate for your system:

Create vics.conf file and edit it:
```
sudo vi /etc/apache2/sites-available/vics.conf
```

```
<VirtualHost *:80>
  DocumentRoot /home/vics/server/

  <Directory /home/vics/server/>
    Require all granted
    AllowOverride All
    Options FollowSymLinks MultiViews

    <IfModule mod_dav.c>
      Dav off
    </IfModule>
  </Directory>
</VirtualHost>
```

On Debian, Ubuntu, and their derivatives, you should run the following command to enable the configuration:

```
sudo a2ensite vics.conf
sudo systemctl reload apache2
```

You must disable default site. 
```
sudo a2dissite 000-default.conf
sudo systemctl reload apache2
```


Then you need restart apache2
```
sudo systemctl restart apache2
```


After restarting Apache you must complete your installation by running either the graphical Installation Wizard, or on the command line with the occ command. To enable this, change the ownership on your vics directories to your HTTP user:

```
chown -R www-data:vics /home/vics/server/
```
