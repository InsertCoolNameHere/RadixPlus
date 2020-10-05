# RADIX+

RADIX+ is a framework designed for the ROOTS-Subterra project that handles the efficient and high-throughput pre-processing, ingestion, storage, retrieval and analytics over large-scale heterogeneous sensor data in a distributed manner. For more details about the project, click [here].

The RADIX+ framework consists of the following components:
1)	A Front-end Visualization Framework [Link](http://radix.cs.colostate.edu/)
2)	Back-end Distributed Storage System for Pre-processing, Storage & Retrieval
3)	Command-based Customizable Data Retrieval for the user with Integrity Check [Link](https://bitbucket.org/InsertCoolNameHere/rig/)

## Introduction

This repository contains the source code and deployment instructions for the RADIX+ DHT, which is the 2nd component in the list, which is built on top of the RADIX distributed storage system detailed [here](https://ieeexplore.ieee.org/abstract/document/8672229). 

The system is built in the form of a zero-hop DHT built over a cluster nodes, where any of the nodes in the cluster can handle data pre-processing, ingestion, geo-referencing, and the redirection of the data records to their relevant nodes. On top of that, the system also handles metadata extraction for fast visual analytics as well as periodic backup of the stored data to a highly available storage for scientific data (Cyverse IRODS).

## Requirements
-  Unix based OS
-  Java 8
-  Netcat Utility Tool

## Deployment
Download this repository and save them at a shared NFS directory for the machines in your cluster. 




### Configuration

The following are the config files that need to be updated in accordance to your cluster requirements:


|Config File|  Description  |Relative Path|
| --------- |:-------------:| -----------:|
|hostnames & 0.group | These two files should be identical and should contain the names of the the hostmachines in the cluster in the format <hostname>:<port>, where <port> can be any available port.	| /galileo/config/network
|prepareFS_az.java |	This file can be used to pass specialized configuration parameters for individual filesystem being housed in the distributed storage. You may need to update this java file and run it with your own filesystem configuration to properly create your filesystem. | /src/dev
|plots_arizona.json | This contains the shapefile for the plots from which sensor data is collected. This shapefile is internally used by RADIX+ to geo-reference ingested sensor data to their corresponding plots. The path to your plots shapefile is to be provided as an argument as shown in the prepareFS.java example. | N/A


### Update Paths & Aliases in bashrc

Add the following lines in your bashrc:
```
export PATH=$PATH:/path/to/project/directory

export GALILEO_HOME=<PROJ_DIRECTORY> 
export GALILEO_CONF=< PROJ_DIRECTORY >/config
export GALILEO_ROOT=<PATH WHERE INDIVIDUAL NODES STORE DISTRIBUTED DATA>


alias gstatus="galileo-cluster -c status"
alias gstart="galileo-cluster -c start"
alias gstop="galileo-cluster -c stop"

```

### Deployment Commands

#### Startup

1.	gstart
   - This starts up the distributed cluster on the nodes you specified
2.	Run prepareFS_az.java
   - This creates a filesystem as per your specifications
3.	echo "<FILE_TO_INSERT>,<FILESYSTEM_NAME>,<SENSOR_TYPE>" | nc <NODE_NAME> 42070
   - This inserts the FILE_TO_INSERT into FILESYSTEM_NAME filesystem. Alternatively, you may write your own java code for data insertion.
   - SENSOR_TYPE is the type of sensor whose data is contained in FILE_TO_INSERT. NODE_NAME is the cluster node that contains <FILE_TO_INSERT>
	
#### Shutdown

1.	gstop


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
