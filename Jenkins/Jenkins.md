# Install Jenkins

## Install Java
````
sudo apt update
sudo apt install openjdk-11-jdk
java -version
````

## Debian/Ubuntu
````
curl -fsSL https://pkg.jenkins.io/debian-stable/jenkins.io.key | sudo tee \
  /usr/share/keyrings/jenkins-keyring.asc > /dev/null

echo deb [signed-by=/usr/share/keyrings/jenkins-keyring.asc] \
  https://pkg.jenkins.io/debian-stable binary/ | sudo tee \
  /etc/apt/sources.list.d/jenkins.list > /dev/null

sudo apt update

sudo apt install jenkins

# password: /var/lib/jenkins/secrets/initialAdminPassword
````

## Start Jenkins
Register the Jenkins service with the command:
````
sudo systemctl daemon-reload
````

You can start the Jenkins service with the command:
````
sudo systemctl start jenkins
````

You can check the status of the Jenkins service using the command:
````
sudo systemctl status jenkins
````