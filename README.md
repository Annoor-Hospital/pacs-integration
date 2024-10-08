# PACS-Integration


#####< IMPORTANT >

Atomfeed set the markers to first page if you don't set it. 

So, Set the markers manually after provisioning and before deployment.

Especially openmrs encounter feed as we are reading encounter feed to figure out the orders.

Use the following sql query to set the markers manually according to the events in your machine. 
(change the last_read_entry_id and feed_uri_for_last_read_entry )

insert into markers (feed_uri, last_read_entry_id, feed_uri_for_last_read_entry) 
    values ('http://loalhost:8080/openmrs/ws/atomfeed/encounter/recent', '?', '?');

Example put script
``` bash
#!/bin/bash

remoteip="${BAHMNIPUT_IP:-x.x.x.x}"

# Check if route exists to remote host
if ! nc -w 3 -z $remoteip 22 2>/dev/null; then
	echo "No route to $remoteip or ssh port not open"
	exit 1
fi

tgt=pacs-integration-annoor

# Remove existing compressed file
if [ -f "${tgt}.jar" ]; then
	rm "${tgt}.jar"
fi

# Build pacs-integration webapp
mvn install 
if [ $? -ne 0 ]; then
    exit 1
fi

# Create jar file
cd pacs-integration-webapp/target/pacs-integration
jar cf "../../../${tgt}.jar" datasetup WEB-INF
cd ../../..

sftp "root@${remoteip}" <<< $'put '"$tgt"'.jar'

# Log in to remote host and extract the jar file into /opt/pacs-integration
ssh root@${remoteip} 'bash -s' << EOF
mv ${tgt}.jar /opt/pacs-integration/
cd /opt/pacs-integration/
rm -rf $tgt
unzip "${tgt}.jar" -d $tgt
chown -R bahmni:bahmni $tgt
EOF
```
