#!/bin/zsh
# zsh does newlines in the variables better
# Monitors processes and checks CPU usage.  Kills if it uses too much.
# if k9delay is specified, it will try to monitor the process for specified time and then
# kill -9 if it doesn't die in the time specified (seconds)

process_name=${pname}
max_cpu=${maxcpu}

LOG_INFO=1
LOG_WARN=2
LOG_ERROR=3
log_level=LOG_WARN

usage() { 
   echo "${1}"
   echo "This will monitor the processes by name and kill processes that are using more than the CPU allowed"
   echo "This can be used in a cron job to periodically monitor troublesome processes"
   echo  1>&2
   echo "Usage: ${1} -p processName -c maxCPU [-m y|N] [-k seconds] [-l info|warn|error]"
   echo "       -p processName is the name (or part of the name) of the process you want to monitor."
   echo "       -c maxCPU is the amount of CPU to tolerate.  If it goes over, kill will be attepted."
   echo "       -m multiple tolerates > 1 processes that match the processName."
   echo "       -k k9Delay is time, in seconds, that the kill attempt should be watched.  kill -9 attempted after."
   echo "       -l is log level (info, warn, error)"
   exit 1
}

log() {
   now=`date +"%y%m%d-%H%M%S"`
   if [[ ${1} -lt ${log_level} ]]; then
      return
   fi
   case ${1} in
      LOG_INFO)
         level="Info"
         ;;
      LOG_WARN)
         level="Warn"
         ;;
      LOG_ERROR)
         level="Error"
         ;;
      *)
         level="Unknown"
         ;;
   esac
   echo "${now} - ${level}: ${2}"
}

multi=false
k9Delay=-1
while getopts ":p:c:m:k:l:" argument; do
    case "${argument}" in
        p)
            processName=${OPTARG}
            ;;
        c)
            maxCPU=${OPTARG}
            ;;
        m)
            [[ ${OPTARG} = 'y' ]] && multi=true || multi=false
            ;;
        k)
            [[ ${OPTARG} != '' ]] && k9Delay=${OPTARG} || k9Delay=-1
            ;;
        l)
            case ${OPTARG} in
               "info")
                  log_level=LOG_INFO
                  ;;
               "warn")
                  log_level=LOG_WARN
                  ;;
               "error")
                  log_level=LOG_ERROR
                  ;;
               *)
                  echo "Invalid log level: ${OPTARG}"
                  exit -1
                  ;;
            esac
            ;;
        *)
            usage ${0}
            ;;
    esac
done
shift $((OPTIND-1))

if [ -z "${processName}" ] || [ -z "${maxCPU}" ]; then
    usage ${0}
fi

processInfo=`ps -Ao pid,pcpu,utime,command | grep ${processName} | grep -v ${0} | grep -v grep`
count=`echo ${processInfo} | wc -l | tr -d ' '`
if [[ ${count} = 0 || ${processInfo} == '' ]]; then
   log LOG_INFO "No process ${processName} found."
   exit 0
elif [[ ${multi} = false && ${count} -gt 1 ]]; then
   log LOG_WARN "Multiple ${processName} (${count}) found."
   exit -2
fi 

echo "${processInfo}" | while IFS= read -r line ; do
   cpu=`echo ${line} | awk '{print $2}'`
   if [[ $cpu -gt maxCPU ]]; then
      log LOG_WARN "Killing ${processName} because CPU ${cpu} > ${maxCPU}"
      pid=`echo ${line} | awk '{print $1}'`
      kill ${pid}

      if [[ $k9Delay -gt 0 ]]; then 
         kill9=false
         for i in {1..${k9Delay}}; do
            # log LOG_INFO "Monitor count: ${i}/${k9Delay}"
            sleep 1
            current=`ps -p ${pid} | grep ${processName}`
            if [[ ${current} == "" ]]; then
               pid=''
               break
            fi
         done
         if [[ ${pid} != "" ]]; then
            log LOG_WARN "Kill -9 ${pid} ${processName} because it did not die"
            kill -9 ${pid}
         fi
      fi
   else
      log LOG_INFO "Process ${processName} is healthy CPU ${cpu} <= ${maxCPU}"
   fi
done
