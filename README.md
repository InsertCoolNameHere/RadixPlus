<p align="center">
  <img width="300" height="300" src="apple-touch-icon-precomposed.png">
</p>

# Radix: High-throughput Georeferencing for Observational Data

Radix is a high-throughput georeferencing and data storage pipeline written in Java 8. It is designed to process large volumes of spatial data. The current version supports data in CSV format. Key features of the system include:
- High rate of data ingestion
- Ability to visualize/query data from the web-based interface
- Simple charting to visualize data trends over time
- Interface can be dockerized to run on any platform
- Data archival in Cyverse's IRODS

The current version is pre-packaged as a Java Archive file, and directory structures are preserved from the original setup here. In the current state, there is a significant amount of unnecessary software included in this repository. This is due to re-use of some code from other applications and fearing breaking dependencies by removing code.

## Requirements
- Linux based OS
- Java 8
- Apache Tomcat
- Netcat Utility Tool
- (Optional) Docker
### For Web-based Interface
- Python 2.7
- Django
- MySQL Database
- Apache HTTP Server

## Installation and Deployment
1. Download the Radix bundle and unpack it to the desired install location
2. Add environment variables for Galileo in .bashrc file
```
nano ~/.bashrc
```
Add these lines:
```
export GALILEO_HOME=<path to galileo directory>
export GALILEO_CONF=<path to galileo directory>/config
export GALILEO_ROOT=/tmp/<username>-galileo
```
3. Install Apache Tomcat
```
sudo apt install tomcat7
```
4. Modify Tomcat username and password in tomcat/conf/tomcat-users.xml
5. Set up cluster configuration files in $GALILEO_CONF/hostnames and tomcat/webapps/galileo-web-service/hostnames.
This is a text file in which each line contains <hostname:port_number>.
6. Run the mkgroups script in $GALILEO_HOME/bin/util to generate group files. Place them in the $GALILEO_CONF/network directory
7. For georeferencing of data points to plots in Radix, a shapefile is necessary. This file must be explicitly named plots.json and placed in $GALILEO_CONF/grid.

## Creating User Accounts
New users can be created with a Python script. This allows a new user access to the data exploration interface. To add a new
user, navigate to the directory containing the manage.py script, and open an interactive Python shell:
```
python
>>> import os
>>> os.environ.setdefault("DJANGO_SETTINGS_MODULE", "columbus.prod_settings")
'columbus.prod_settings'
>>> import django
>>> django.setup()
>>> from django.contrib.auth.models import User
>>> user=User.objects.create_user(username='your_user_name', password='your_password')
>>> user.save()
>>> quit()
```


# License
Copyright (c) 2018, Computer Science Department, Colorado State University
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

This software is provided by the copyright holders and contributors "as is" and
any express or implied warranties, including, but not limited to, the implied
warranties of merchantability and fitness for a particular purpose are
disclaimed. In no event shall the copyright holder or contributors be liable for
any direct, indirect, incidental, special, exemplary, or consequential damages
(including, but not limited to, procurement of substitute goods or services;
loss of use, data, or profits; or business interruption) however caused and on
any theory of liability, whether in contract, strict liability, or tort
(including negligence or otherwise) arising in any way out of the use of this
software, even if advised of the possibility of such damage.
