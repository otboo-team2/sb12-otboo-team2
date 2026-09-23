#!/usr/bin/env bash
#
# Pinterest API 가 지금 쓸 수 있는 상태인지 판정합니다.
#
# trial 승인 대기 중에는 토큰이 발급돼도 모든 v5 엔드포인트가 막혀 있다.
# "토큰이 있다"와 "API 가 동작한다"는 다른 얘기라, 배치를 돌리기 전에 이걸로 확인한다.
#
# 사용법
#   read -rs "PINTEREST_ACCESS_TOKEN?Pinterest 토큰 붙여넣기: " && export PINTEREST_ACCESS_TOKEN
#   ./scripts/pinterest-check.sh
#
# 종료 코드: 0 = 사용 가능, 1 = 사용 불가
#
set -uo pipefail

if [[ -z "${PINTEREST_ACCESS_TOKEN:-}" ]]; then
    cat >&2 <<'MSG'
PINTEREST_ACCESS_TOKEN 이 없습니다.

토큰이 셸 히스토리에 남지 않게 이렇게 넣으세요(입력해도 화면에 안 보입니다):

    read -rs "PINTEREST_ACCESS_TOKEN?Pinterest 토큰 붙여넣기: " && export PINTEREST_ACCESS_TOKEN
MSG
    exit 1
fi

# 토큰을 curl 인자로 넘기면 같은 머신의 다른 사용자가 ps 로 볼 수 있다. 설정 파일로 넘긴다.
CURL_CONFIG="$(mktemp)"
trap 'rm -f "$CURL_CONFIG"' EXIT
chmod 600 "$CURL_CONFIG"
printf 'header = "Authorization: Bearer %s"\n' "$PINTEREST_ACCESS_TOKEN" > "$CURL_CONFIG"

usable=1

probe() {
    local label="$1" base="$2"
    local body code error_code

    body="$(curl -sS -K "$CURL_CONFIG" -w $'\n%{http_code}' --max-time 20 \
        "${base}/v5/user_account" 2>&1)"
    code="${body##*$'\n'}"
    body="${body%$'\n'*}"

    error_code="$(python3 -c '
import json, sys
try:
    print(json.loads(sys.stdin.read()).get("code", ""))
except Exception:
    print("")
' <<< "$body" 2>/dev/null)"

    printf '%-12s HTTP %-4s ' "$label" "$code"

    case "$code" in
        200)
            echo "✅ 사용 가능"
            echo "             $(python3 -c '
import json, sys
d = json.loads(sys.stdin.read())
print("계정:", d.get("username") or d.get("id") or "(이름 없음)", "/ 타입:", d.get("account_type", "?"))
' <<< "$body" 2>/dev/null)"
            usable=0
            ;;
        401)
            if [[ "$error_code" == "3" ]]; then
                echo "❌ 앱이 아직 활성화되지 않았습니다 (code 3)"
                echo "             trial 승인이 나야 풀립니다. 토큰 문제가 아닙니다."
            elif [[ "$error_code" == "2" ]]; then
                echo "❌ 토큰이 잘못됐거나 만료됐습니다 (code 2)"
                echo "             sandbox 토큰은 30일짜리입니다. 콘솔에서 재발급하세요."
            else
                echo "❌ 인증 실패 (code ${error_code:-?})"
                echo "             $body"
            fi
            ;;
        429)
            echo "⚠️  호출 한도 초과 — API 자체는 열려 있습니다"
            usable=0
            ;;
        000)
            echo "❌ 연결 실패 (네트워크 · DNS · 타임아웃)"
            ;;
        *)
            echo "❓ 예상하지 못한 응답"
            echo "             $body"
            ;;
    esac
}

echo "Pinterest API 상태 확인"
echo "─────────────────────────────────────────────"
probe "sandbox" "https://api-sandbox.pinterest.com"
probe "production" "https://api.pinterest.com"
echo "─────────────────────────────────────────────"

if [[ "$usable" -eq 0 ]]; then
    echo "적어도 한 환경은 사용 가능합니다. 위에서 ✅ 인 쪽의 주소를 PINTEREST_BASE_URL 에 넣으세요."
else
    echo "아직 쓸 수 없습니다. 배포 환경에는 PINTEREST_ACCESS_TOKEN 과 PINTEREST_BOARD_IDS 를"
    echo "빈 값으로 두고, pinterestSyncJob 은 실행하지 마세요 (앱 기동에는 영향이 없습니다)."
fi

exit "$usable"
