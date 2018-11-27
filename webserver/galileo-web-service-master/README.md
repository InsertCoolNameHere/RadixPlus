A RESTful websevice implementation for Galileo Spacetime Storage System. Key services include obtaining list of filesystems, their overview, list of features in a filesystem, blocks, and featureset.

## Requirements
* Java Runtime Version 1.8.0
* Apache Tomcat 8
* Apache Ant

## Installation
This section describes the installation and configuration of the requirements on Google compute engine instance running Debian Jessie.
1. Install Java 8
    ```sh
    $ echo "deb http://ppa.launchpad.net/webupd8team/java/ubuntu xenial main" | \
        sudo tee /etc/apt/sources.list.d/webupd8team-java.list
    $ echo "deb-src http://ppa.launchpad.net/webupd8team/java/ubuntu xenial main" | \
        sudo tee -a /etc/apt/sources.list.d/webupd8team-java.list
    $ sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys EEA14886
    $ sudo apt-get update
    $ sudo apt-get install psmisc
    $ sudo apt-get install oracle-java8-installer
    $ sudo apt-get install oracle-java8-set-default
    ```
2. Set up `JAVA_HOME` environment variable
    ```sh
    $ cd
    $ vi .bashrc
    # Append the following to the end of the file
    # environment settings
    export JAVA_HOME=/usr/lib/jvm/java-8-oracle
    $ source .bashrc
    ``` 

3. Install Apache Tomcat 8
    ```sh
    $ sudo apt-get install tomcat8
    $ sudo apt-get install tomcat8-admin tomcat8-examples tomcat8-docs
    ```
    
4. Update the `tomcat-users.xml` file to access the manager portal. Change the username and password.
    ```sh
    sudo vi /etc/tomcat8/tomcat-users.xml
    # Paste the below contents
    <role rolename="admin"/>
    <role rolename="admin-gui"/>
    <role rolename="manager-gui"/>
    <user username="admin" password="admin" roles="admin,admin-gui,manager-gui"/>
    ```

5. Configure Tomcat.
    ```sh
    $ sudo vi /etc/default/tomcat8
    ```
    1. Set or update `JAVA_HOME` in Tomcat. See if `JAVA_HOME` is present and commented out. Accordingly uncomment and/or change the line to
        ```sh
        JAVA_HOME=/usr/lib/jvm/java-8-oracle
        ```
       
     2. Increase Tomcat JVM heap size. Uncomment the below line if present
        ```sh
        JAVA_OPTS="-Djava.awt.headless=true -Xmx128m -XX:+UseConcMarkSweepGC"
        ```
        and/or change it to
        ```sh
        JAVA_OPTS="-Djava.awt.headless=true -Xmx4g -XX:+UseConcMarkSweepGC"
        ```
        
     3. Save the file and restart Tomcat
        ```sh
        $ sudo service tomcat8 restart
        ```
        If the command fails, run the following command and see the error
        ```sh
        $ sudo systemctl status tomcat8.service
        ```
        Logs are stored at `/var/log/tomcat8`
        
     4. Update Tomcat limits
        ```sh
        $ sudo vi /etc/init.d/tomcat8
        ```
        Add the following lines right after the line `TOMCAT8_GROUP=tomcat8`.
        ```sh
        ulimit -Hn 16384
        ulimit -Sn 16384
        ```
        Save the file and restart Tomcat
        ```sh
        $ sudo systemctl daemon-reload
        $ sudo service tomcat8 restart
        ```
        Verify that the changes are applied
        ```sh
        $ ps -u tomcat8
        # Make note of the tomcat8 process id (pid)
        $ cat /proc/<pid of tomcat8>/limits
        ```
        
6. Navigate to the url `<domain-name or ip-address>:8080/manager` and you should see the manager portal of Tomcat. If server refuses to connect, check the firewall rules in the Google cloud console. Allow tcp:8080 and tcp:8443 to make the compute engine instance serve traffic on http:8080 and https:8443

7. Set up `CATALINA_HOME` environment variable 
    ```sh
    $ cd
    $ vi .bashrc
    # Append the following to the end of the file
    # environment settings
    export CATALINA_HOME=/usr/share/tomcat8
    $ source .bashrc
    ```

8. Install Apache Ant
    ```sh
    $ sudo apt-get install ant
    ```

## Deployment

1. Download the distribution
    ```sh
    $ cd
    $ sudo apt-get install wget unzip
    $ wget https://github.com/jkachika/galileo-web-service/archive/master.zip
    $ unzip master.zip
    ```
    
2. Update the hostnames to the list of hosts running Galileo and save the file
    ```sh
    $ cd galileo-web-service-master/WebContent/WEB-INF
    $ vi hostnames
    ```

3. Build using Ant. When the build is successful, the WAR file is placed in `dist` folder.
    ```sh
    $ cd
    $ cd galileo-web-service-master
    $ ant
    ```

4. Deploy the WAR file using Tomcat manager console.
