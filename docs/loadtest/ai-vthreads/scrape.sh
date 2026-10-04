#!/bin/bash
# 실험 태스크의 관리 포트(8081)에서 5초마다 필요한 지표만 떠서 남긴다. Prometheus 설정은 건드리지 않는다.
# usage: scrape.sh <task-ip> <out-file>
IP=$1; OUT=$2
PAT='^(otboo_recommendation_|jvm_threads_(live|peak|daemon|states)_|jvm_memory_used_bytes\{.*area="heap"|jvm_gc_pause_seconds_(count|sum)|process_cpu_usage|system_cpu_usage|hikaricp_connections(_active|_pending|_idle|_max)?\{|hikaricp_connections_acquire_seconds_(count|sum|max)|tomcat_threads_|http_server_requests_active_seconds_gcount|executor_(active|pool_size)_)'
while true; do
  TS=$(date +%s)
  curl -s -m 4 "http://$IP:8081/actuator/prometheus" | grep -E "$PAT" | sed "s/^/$TS /" >> "$OUT"
  sleep 5
done
