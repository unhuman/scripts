#!/bin/zsh
# zsh does newlines in the variables better
# Monitors processes and checks CPU usage.  Kills if it uses too much.
# if k9delay is specified, it will try to monitor the process for specified time and then
# kill -9 if it doesn't die in the time specified (seconds)

process_name=${pname}
max_cpu=${maxcpu}

usage() { 
   echo "${1}" 1>&2
   echo "This will monitor the processes by name and kill processes that are using more than the CPU allowed" 1>&2
   echo "This can be used in a cron job to periodically monitor troublesome processes" 1>&2
   echo  1>&2
   echo "Usage: ${1} -p processName -c maxCPU [-m y|N] [-k seconds]" 1>&2
   echo "       -p processName is the name (or part of the name) of the process you want to monitor." 1>&2
   echo "       -c maxCPU is the amount of CPU to tolerate.  If it goes over, kill will be attepted." 1>&2
   echo "       -m multiple tolerates > 1 processes that match the processName." 1>&2
   echo "       -k k9Delay is time, in seconds, that the kill attempt should be watched.  kill -9 attempted after." 1>&2
   exit 1
}

multi=false
k9Delay=-1
while getopts ":p:c:m:k:" argument; do
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
   echo "Notice: No process ${processName} found." 1>&2
   exit 0
elif [[ ${multi} = false && ${count} -gt 1 ]]; then
   echo "Error: Multiple ${processName} (${count}) found." 1>&2
   exit -2
fi 

echo "${processInfo}" | while IFS= read -r line ; do
   cpu=`echo ${line} | awk '{print $2}'`
   if [[ $cpu -gt maxCPU ]]; then
      echo "Notice: Killing ${processName} because CPU ${cpu} > ${maxCPU}" 1>&2
      pid=`echo ${line} | awk '{print $1}'`
      kill ${pid}

      if [[ $k9Delay -gt 0 ]]; then 
         kill9=false
         for i in {1..${k9Delay}}; do
            # echo "Monitor count: ${i}/${k9Delay}" 1>&2
            sleep 1
            current=`ps -p ${pid} | grep ${processName}`
            if [[ ${current} == "" ]]; then
               pid=''
               break
            fi
         done
         if [[ ${pid} != "" ]]; then
            echo "Notice: Kill -9 ${pid} ${processName} because it did not die" 1>&2
            kill -9 ${pid}
         fi
      fi
   else
      echo "Process ${processName} is healthy"
   fi
done
