#!/usr/bin/env bash
# Copyright 1999-2019 Seata.io Group.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at、
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

if [[ $# != 1 ]]; then
	echo "USAGE: $0 nacosAddr"
	exit 1
fi

nacosAddr=$1
echo "set nacosAddr=$nacosAddr"
contentType="content-type:application/json;charset=UTF-8"

failCount=0
tempLog=$(mktemp -t nacos-config.log)
function addConfig() {
  curl -X POST -H ${1} "http://$2/nacos/v1/cs/configs?dataId=$3&group=SEATA_GROUP&content=$4" >${tempLog} 2>/dev/null
  if [[ -z $(cat ${tempLog}) ]]; then
    echo "Please check the cluster status."
    exit 1
  fi
  if [[ $(cat ${tempLog}) =~ "true" ]]; then
    echo "Set" "${3}" "=" "${4} >>> success"
  else
    echo "Set" "${3}" "=" "${4} >>> fail"
    (( failCount++ ))
  fi
}

count=0
for line in $(cat $(dirname "$PWD")/config.txt); do
  (( count++ ))
	key=${line%%=*}
  value=${line#*=}
	addConfig ${contentType} ${nacosAddr} ${key} ${value}
done

echo "========================================================================="
echo "  Complete initialization parameters, total-count:$count, failure-count:$failCount "
echo "========================================================================="

if [[ ${failCount} -eq 0 ]]; then
	echo "Init nacos config finished, please start seata-server."
else
	echo "init nacos config fail."
fi