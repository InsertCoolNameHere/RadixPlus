# SILO

SILO is a framework designed for the ROOTS-Subterra project that handles the efficient and high-throughput pre-processing, ingestion, storage, retrieval and analytics over large-scale heterogeneous sensor data in a distributed manner. For more details about the project, click [here].

The SILO framework consists of the following components:
1)	A Front-end Visualization Framework [Link]
2)	Back-end Distributed Storage System for Pre-processing, Storage & Retrieval
3)	Command-based Customizable Data Retrieval for the user with Integrity Check [Link]

## Introduction

This repository contains the source code and deployment instructions for the SILO DHT, which is the 2nd component in the list, which is built on top of the RADIX distributed storage system detailed [here](https://ieeexplore.ieee.org/abstract/document/8672229). 

The system is built in the form of a zero-hop DHT built over a cluster nodes, where any of the nodes in the cluster can handle data pre-processing, ingestion, geo-referencing, and the redirection of the data records to their relevant nodes. On top of that, the system also handles metadata extraction for fast visual analytics as well as periodic backup of the stored data to a highly available storage for scientific data (Cyverse IRODS).

## Requirements
-  Linux based OS
-  Java 8
-  Netcat Utility Tool

## Deployment
Download this repository and save them at a shared NFS directory for the machines in your cluster. 




### Configuration

The following are the config files that need to be updated in accordance to your cluster requirements:


|Config File|  Description  |Relative Path|
| --------- |:-------------:| -----------:|
|hostnames & 0.group | These two files should be identical and should contain the names of the the hostmachines in the cluster in the format <hostname>:<port>, where <port> can be any available port.	/galileo/config/network
|prepareFS_az.java	This file can be used to pass specialized configuration parameters for individual filesystem being housed in the distributed storage. You may need to update this java file and run it with your own filesystem configuration to properly create your filesystem. | /src/dev
|plots_arizona.json | This contains the shapefile for the plots from which sensor data is collected. This shapefile is internally used by SILO to geo-reference ingested sensor data to their corresponding plots. The path to your plots shapefile is to be provided as an argument as shown in the prepareFS.java example. | N/A




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

## Data Ingestion
Before data ingestion can begin, first ensure that the Galileo/Radix cluster is up and ready to begin.
```
./galileo-cluster start
```
Wait a few seconds for the cluster to initialize and read configuration files. The cluster status can be checked with
```
./galileo-cluster status
```
Once all nodes display "Online", the cluster is ready to begin ingesting data.
For data ingestion, first select which nodes should act as ingest nodes, and place data on those machines, ensuring that the data file name and path is the same on each machine. Now the filesystem must be created and the features defined. This is an example:
```
Connector connector = new Connector();
List<Pair<String, FeatureType>> featureList = new ArrayList<>();
//features must be in order which they appear in raw data
featureList.add(new Pair<>("time", FeatureType.STRING));
		
featureList.add(new Pair<>("lat", FeatureType.DOUBLE));
featureList.add(new Pair<>("long", FeatureType.DOUBLE));
featureList.add(new Pair<>("plotID", FeatureType.INT));
featureList.add(new Pair<>("temperature", FeatureType.DOUBLE));
featureList.add(new Pair<>("humidity", FeatureType.DOUBLE));
featureList.add(new Pair<>("CO2", FeatureType.DOUBLE));
featureList.add(new Pair<>("genotype", FeatureType.STRING));
featureList.add(new Pair<>("rep", FeatureType.INT));
for (int i = 1; i < 7; i++)
	featureList.add(new Pair<>("Random"+i, FeatureType.DOUBLE));
featureList.add(new Pair<>("Random7", FeatureType.STRING));
SpatialHint spatialHint = new SpatialHint("lat", "long");

FilesystemRequest fsRequest = new FilesystemRequest(
"roots", FilesystemAction.CREATE, featureList, spatialHint);
fsRequest.setNodesPerGroup(5);
fsRequest.setPrecision(11);
fsRequest.setTemporalType(TemporalType.HOUR_OF_DAY);

//Any Galileo storage node hostname and port number
NetworkDestination storageNode = new NetworkDestination("lattice-100.cs.colostate.edu", 5635);
connector.publishEvent(storageNode, fsRequest);
Thread.sleep(2500);
connector.close();
```
Once this is complete, data can be ingested by either sending the command to each ingest node manually, or using the convenience script, start-ingest.sh. The script accepts two arguments, the path to the file containing the ingest node machine names or addresses, and the path to the ingest data on each machine.
```
./start-ingest.sh /path/to/ingest_node_config_file /path/to/ingest_data
```
Changes should be reflected within a few seconds at the data dashboard at radix.cs.colostate.edu.
In the current state, Radix only accepts CSV files, and only of a particular format. It is required that the first 3 fields be timestamp, longitude, latitude. The remaining fields are currently hard-coded as:
plotID(int), temperature(double), humidity(double), CO2(double), genotype(string), 6 random double values, rep(double), random boolean value. These values are used in galileo.dht.DataStoreHandler. 

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
