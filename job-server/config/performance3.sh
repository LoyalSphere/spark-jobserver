#!/usr/bin/env bash

# Environment and deploy file

APP_USER=spark
APP_GROUP=spark
JMX_PORT=9999
INSTALL_DIR=/opt/sjs
LOG_DIR=/var/log/spark-job-server
PIDFILE=spark-jobserver.pid
JOBSERVER_MEMORY=1G
SPARK_VERSION=1.6.0
MAX_DIRECT_MEMORY=512M
SPARK_HOME=/usr/hdp/current/spark-client
SPARK_CONF_DIR=$SPARK_HOME/conf
SCALA_VERSION=2.11.12

MANAGER_JAR_FILE="$appdir/spark-job-server.jar"
MANAGER_CONF_FILE="$(basename $conffile)"
MANAGER_EXTRA_JAVA_OPTIONS=
MANAGER_EXTRA_SPARK_CONFS="spark.yarn.submit.waitAppCompletion=false|spark.files=$appdir/log4jcluster.properties,$conffile"
MANAGER_LOGGING_OPTS="-Dlog4j.configuration=log4j-cluster.properties"
SPARK_LAUNCHER_VERBOSE=0
