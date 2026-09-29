#!/usr/bin/env bash
#
# 보드 안 핀 전체의 description 끝에 @otboo 태그 줄을 한 번에 붙인다.
#
# 핀 하나하나 Pinterest 앱에서 열어 태그를 적는 대신, 보드 하나에 적용할 태그를
# 한 번만 정해서 이 스크립트로 일괄 반영한다. OutfitTagParser 가 읽는 문법(@otboo
# temp:.. sky:.. style:.. item:.. gender:..)을 그대로 쓰므로 백엔드는 안 건드린다.
#
# 이미 @otboo 줄이 있는 핀은 건드리지 않는다 — 수동으로 예외 태그를 준 핀일 수 있다.
# 그래서 재실행해도 안전하다(이미 태그된 핀은 매번 건너뛴다).
#
# 사용법 (기본은 미리보기만, 실제로 바꾸려면 --apply)
#   read -rs "PINTEREST_ACCESS_TOKEN?Pinterest 토큰 붙여넣기: " && export PINTEREST_ACCESS_TOKEN
#   ./scripts/pinterest-bulk-tag.sh <board_id> "temp:5-8 sky:cloudy style:minimal item:knit,coat gender:unisex"
#   ./scripts/pinterest-bulk-tag.sh <board_id> "..." --apply
#
# 옵션
#   --apply          실제로 PATCH 한다 (기본은 dry-run — 무엇을 바꿀지만 보여준다)
#   --base-url URL   기본 https://api.pinterest.com (sandbox 는 https://api-sandbox.pinterest.com)
#
# 종료 코드: 0 = 정상 종료, 1 = 인자·토큰 오류, 2 = 하나 이상의 핀에서 실패
#
set -uo pipefail

BASE_URL="https://api.pinterest.com"
APPLY=0

usage() {
    cat >&2 <<'MSG'
사용법: ./scripts/pinterest-bulk-tag.sh <board_id> "<@otboo 뒤에 올 태그 본문>" [--apply] [--base-url URL]

예)
  ./scripts/pinterest-bulk-tag.sh 123456789 "temp:5-8 sky:cloudy style:minimal item:knit,coat gender:unisex"
  ./scripts/pinterest-bulk-tag.sh 123456789 "temp:5-8 sky:cloudy style:minimal item:knit,coat gender:unisex" --apply
MSG
}

POSITIONAL=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        --apply) APPLY=1; shift ;;
        --base-url) BASE_URL="$2"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) POSITIONAL+=("$1"); shift ;;
    esac
done
set -- "${POSITIONAL[@]+"${POSITIONAL[@]}"}"

if [[ $# -lt 2 ]]; then
    usage
    exit 1
fi
BOARD_ID="$1"
TAG_BODY="$2"

if [[ -z "${PINTEREST_ACCESS_TOKEN:-}" ]]; then
    cat >&2 <<'MSG'
PINTEREST_ACCESS_TOKEN 이 없습니다.

토큰이 셸 히스토리에 남지 않게 이렇게 넣으세요(입력해도 화면에 안 보입니다):

    read -rs "PINTEREST_ACCESS_TOKEN?Pinterest 토큰 붙여넣기: " && export PINTEREST_ACCESS_TOKEN
MSG
    exit 1
fi

if ! [[ "$BOARD_ID" =~ ^[0-9]{1,64}$ ]]; then
    echo "board_id 는 숫자만 가능합니다: $BOARD_ID" >&2
    exit 1
fi

# 토큰을 curl 인자로 넘기면 같은 머신의 다른 사용자가 ps 로 볼 수 있다. 설정 파일로 넘긴다.
CURL_CONFIG="$(mktemp)"
trap 'rm -f "$CURL_CONFIG"' EXIT
chmod 600 "$CURL_CONFIG"
printf 'header = "Authorization: Bearer %s"\n' "$PINTEREST_ACCESS_TOKEN" > "$CURL_CONFIG"

if [[ "$APPLY" -eq 1 ]]; then
    echo "⚠️  --apply 모드: 실제로 핀 description 을 수정합니다."
else
    echo "🔍 dry-run 모드: 아무것도 바꾸지 않습니다. 실제로 반영하려면 --apply 를 붙이세요."
fi
echo "board_id = $BOARD_ID"
echo "태그       = @otboo $TAG_BODY"
echo "─────────────────────────────────────────────"

failed=0
tagged=0
skipped=0
bookmark=""

while :; do
    url="${BASE_URL}/v5/boards/${BOARD_ID}/pins?page_size=100"
    if [[ -n "$bookmark" ]]; then
        url="${url}&bookmark=${bookmark}"
    fi

    response="$(curl -sS -K "$CURL_CONFIG" --max-time 30 "$url")"

    if ! python3 -c 'import json,sys; json.loads(sys.stdin.read())' <<< "$response" 2>/dev/null; then
        echo "❌ Pinterest 응답이 JSON 이 아닙니다(네트워크 오류이거나 인증 실패일 수 있습니다):" >&2
        echo "$response" >&2
        exit 1
    fi

    # description 에 개행·탭이 섞여 있을 수 있어 base64 로 감싸 한 줄씩("id\tbase64") 뽑는다.
    pins="$(python3 -c '
import json, sys, base64
data = json.loads(sys.stdin.read())
for item in data.get("items", []):
    pin_id = item.get("id", "")
    desc = item.get("description") or ""
    print(pin_id + "\t" + base64.b64encode(desc.encode()).decode())
' <<< "$response")"

    if [[ -n "$pins" ]]; then
        while IFS=$'\t' read -r pin_id desc_b64; do
            [[ -z "$pin_id" ]] && continue
            description="$(python3 -c "import base64,sys; sys.stdout.write(base64.b64decode(sys.argv[1]).decode())" "$desc_b64")"

            if grep -qiE '^@otboo(\s|$)' <<< "$description"; then
                skipped=$((skipped + 1))
                continue
            fi

            if [[ -z "$description" ]]; then
                new_description="@otboo ${TAG_BODY}"
            else
                new_description="${description}"$'\n\n'"@otboo ${TAG_BODY}"
            fi

            if [[ "$APPLY" -ne 1 ]]; then
                echo "would tag: pin_id=$pin_id"
                tagged=$((tagged + 1))
                continue
            fi

            payload_file="$(mktemp)"
            python3 -c "import json,sys; print(json.dumps({'description': sys.argv[1]}))" "$new_description" > "$payload_file"

            patch_code="$(curl -sS -K "$CURL_CONFIG" -o /dev/null -w '%{http_code}' --max-time 20 \
                -X PATCH "${BASE_URL}/v5/pins/${pin_id}" \
                -H "Content-Type: application/json" \
                --data @"$payload_file")"
            rm -f "$payload_file"

            if [[ "$patch_code" == "200" ]]; then
                echo "tagged:    pin_id=$pin_id"
                tagged=$((tagged + 1))
            else
                echo "❌ failed: pin_id=$pin_id (HTTP $patch_code)" >&2
                failed=$((failed + 1))
            fi

            # Trial 등급은 하루 호출 상한이 앱 단위다. 한도를 급하게 태우지 않게 쉬어 간다.
            sleep 0.3
        done <<< "$pins"
    fi

    bookmark="$(python3 -c 'import json,sys; print(json.loads(sys.stdin.read()).get("bookmark") or "")' <<< "$response")"
    [[ -z "$bookmark" ]] && break
done

echo "─────────────────────────────────────────────"
echo "완료: 태그됨=$tagged 건너뜀(이미 태그됨)=$skipped 실패=$failed"

if [[ "$failed" -gt 0 ]]; then
    exit 2
fi
exit 0
