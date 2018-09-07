#!/bin/bash
# Script to start the job server
# Extra arguments will be spark-submit options, for example
#  ./server_start.sh --jars cassandra-spark-connector.jar
#
# Environment vars (note settings.sh overrides):
#   JOBSERVER_MEMORY - defaults to 1G, the amount of memory (eg 512m, 2G) to give to job server
#   JOBSERVER_CONFIG - alternate configuration file to use
#   JOBSERVER_FG    - launches job server in foreground; defaults to forking in background
set -e

get_abs_script_path() {
  pushd . >/dev/null
  cd "$(dirname "$0")"
  appdir=$(pwd)
  popd  >/dev/null
}

get_abs_script_path

set -a
. $appdir/setenv.sh
set +a

GC_OPTS_SERVER="$GC_OPTS_BASE -Xloggc:$LOG_DIR/$GC_OUT_FILE_NAME"

MAIN="spark.jobserver.JobServer"

if [ -z "$PID_DIR" ]; then
  pidFilePath=$appdir/$PIDFILE
else
  mkdir -p "$PID_DIR"
  pidFilePath="$PID_DIR/$PIDFILE"
fi

if [ -f "$pidFilePath" ] && kill -0 $(cat "$pidFilePath"); then
   echo 'Job server is already running'
   exit 1
fi

cmd='$SPARK_HOME/bin/spark-submit --class $MAIN --driver-memory $JOBSERVER_MEMORY
  --conf "spark.executor.extraJavaOptions=$LOGGING_OPTS"
  --driver-java-options "$GC_OPTS_SERVER $JAVA_OPTS_SERVER $LOGGING_OPTS $CONFIG_OVERRIDES"
  $@ $appdir/spark-job-server.jar $conffile'
echo "$cmd"
if [ -z "$JOBSERVER_FG" ]; then
  eval $cmd > $LOG_DIR/server_start.log 2>&1 < /dev/null &
  echo $! > $pidFilePath
else
  eval $cmd
fi
