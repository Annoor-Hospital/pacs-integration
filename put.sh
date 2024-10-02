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
