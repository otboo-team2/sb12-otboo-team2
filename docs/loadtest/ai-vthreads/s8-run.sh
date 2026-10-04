#!/bin/bash
# s8 한 회차: 모의 서버 초기화·지연 설정 → 서버 지표 수집 시작 → k6 → 모의 서버 통계 → 집계.
# 팀 디스코드 알림은 보내지 않는다(run.sh 를 쓰지 않는 이유. run.sh 는 운영 서비스 버전을 기록한다).
#
# usage: COND=A THREAD=platform CAP=20 TASK_INFO="..." AI_STEPS=0.1,1,2,4 STEP_DUR=120 WARMUP=60 \
#          s8-run.sh <label> <task-ip> '<mock-config-json>'
set -uo pipefail
LABEL=$1; IP=$2; MOCKCFG=$3
: "${AI_STEPS:=0.1,1,2,4}" "${STEP_DUR:=120}" "${WARMUP:=60}" "${WARMUP_AI:=0.2}" "${DRAIN:=60}"
: "${FEED_RPS:=5}" "${USERS:=50}" "${WEATHER_ID:=}" "${PROMPT_MODE:=unique}" "${MOCK:=http://localhost:18090}"
TS=$(date -u +%Y%m%dT%H%M%SZ)
D=/opt/k6/results/s8/${LABEL}-${TS}; mkdir -p "$D"; cd /opt/k6

export LOADTEST_PW=$(aws secretsmanager get-secret-value --region ap-northeast-2 --secret-id otboo/loadtest \
  --query SecretString --output text | grep -vE '^\s*#|^\s*$' | head -1 | sed 's/^PASSWORD=//' | tr -d '\r\n')
export BASE=http://$IP:8080

if ! curl -sf -m 5 "http://$IP:8081/actuator/health" > /dev/null; then echo "task $IP not healthy"; exit 1; fi
curl -s -XPOST "$MOCK/__reset" > /dev/null
curl -s -XPOST "$MOCK/__config" -d "$MOCKCFG" > "$D/mock-config.json"

python3 - "$D/meta.json" <<PY
import json, sys, os
json.dump({"label": "$LABEL", "cond": os.environ.get("COND"), "thread": os.environ.get("THREAD"),
           "cap": os.environ.get("CAP"), "task_info": os.environ.get("TASK_INFO"), "ip": "$IP",
           "mock_config": json.load(open("$D/mock-config.json")), "started": "$TS",
           "AI_STEPS": "$AI_STEPS", "STEP_DUR": "$STEP_DUR", "WARMUP": "$WARMUP", "WARMUP_AI": "$WARMUP_AI",
           "DRAIN": "$DRAIN", "FEED_RPS": "$FEED_RPS", "USERS": "$USERS", "WEATHER_ID": "$WEATHER_ID",
           "PROMPT_MODE": "$PROMPT_MODE", "k6": os.popen("k6 version").read().strip()},
          open(sys.argv[1], "w"), ensure_ascii=False, indent=1)
PY

/opt/k6/ai-vthreads/scrape.sh "$IP" "$D/scrape.txt" & SP=$!
k6 run --out "csv=$D/k6.csv.gz" --summary-export="$D/summary-export.json" \
  -e AI_STEPS="$AI_STEPS" -e STEP_DUR="$STEP_DUR" -e WARMUP="$WARMUP" -e WARMUP_AI="$WARMUP_AI" \
  -e DRAIN="$DRAIN" -e FEED_RPS="$FEED_RPS" -e USERS="$USERS" -e WEATHER_ID="$WEATHER_ID" \
  -e PROMPT_MODE="$PROMPT_MODE" -e RUN="$LABEL" s8-ai-vthreads.js > "$D/k6.log" 2>&1
echo "k6 exit=$?" >> "$D/k6.log"
sleep 10; kill $SP 2>/dev/null
curl -s "$MOCK/__stats" > "$D/mock-stats.json"
python3 /opt/k6/ai-vthreads/analyze.py "$D" | tee "$D/summary.md"
echo "dir=$D"
