#!/usr/bin/env python3
"""OTBOO AI 추천 실험용 가짜 OpenAI (과금 0). 표준 라이브러리만 쓴다.

v1(mock-openai.py)과 다른 점
- 응답이 앱의 파서·검증을 실제로 통과한다. 조건 추출은 스키마 4필드, 최종 생성은 후보 중에서
  OotdCombinationPolicy(타입 중복 금지, DRESS 와 TOP/BOTTOM 동시 금지)를 지키는 조합을 고른다.
  그래서 "AI 추천 성공 경로"가 끝까지 재현된다.
- 지연을 단계별로 준다: condition / embedding / generation / metadata / index_embedding.
  어느 단계에 몇 ms 를 줬는지가 실험 조건이다.
- 통계를 단계별로 센다(수신 수, 동시 처리 최대치, 클라이언트가 먼저 끊은 수).

조작
  POST /__config {"delay_ms": {"condition": 8000, "embedding": 8000, "generation": 8000}, "jitter_ms": 0, "p500": 0}
       delay_ms 에 숫자 하나를 주면 요청 경로 3단계(condition·embedding·generation)에 같은 값을 준다.
  GET  /__stats   POST /__reset
실행
  systemd-run --unit mock-openai-v2 --property=Restart=on-failure /usr/bin/python3 mock-openai-v2.py 18090
"""
import hashlib
import json
import random
import sys
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

API_KEY = "mock-key-no-billing"
REQUEST_STAGES = ("condition", "embedding", "generation")
STAGES = REQUEST_STAGES + ("metadata", "index_embedding", "unknown")
ALLOWED_TYPES = {"TOP", "BOTTOM", "DRESS", "OUTER", "SHOES", "ACCESSORY", "HAT", "BAG"}

lock = threading.Lock()
config = {"delay_ms": {s: 0 for s in STAGES}, "jitter_ms": 0, "p500": 0.0}


def fresh_stats():
    return {
        "since": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "by_stage": {s: {"received": 0, "ok": 0, "err500": 0, "client_gone": 0,
                         "in_flight": 0, "in_flight_max": 0} for s in STAGES},
        "in_flight": 0, "in_flight_max": 0, "unauthorized": 0,
    }


stats = fresh_stats()


def now_iso():
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def classify(path, body):
    if path.endswith("/embeddings"):
        text = body.get("input")
        text = text[0] if isinstance(text, list) and text else text
        # 질의 임베딩은 RecommendationQueryEmbeddingService.queryText 가 "요청:" 으로 시작한다
        return "embedding" if isinstance(text, str) and text.startswith("요청:") else "index_embedding"
    if path.endswith("/responses"):
        name = (body.get("tool_choice") or {}).get("name")
        return {"extract_recommendation_condition": "condition",
                "select_recommendation_clothes": "generation",
                "analyze_recommendation_clothes_metadata": "metadata"}.get(name, "unknown")
    return "unknown"


def function_call(name, arguments):
    return {"id": "resp_mock", "object": "response", "status": "completed",
            "output": [{"type": "function_call", "id": "fc_mock", "call_id": "call_mock",
                        "name": name, "arguments": json.dumps(arguments, ensure_ascii=False)}]}


def pick_outfit(body):
    """후보 목록 순서대로 타입이 겹치지 않고 DRESS/TOP·BOTTOM 이 섞이지 않게 고른다."""
    content = body["input"][0]["content"]
    clothes = json.loads(content).get("clothes", [])
    chosen, types = [], set()
    for item in clothes:
        t = item.get("type")
        if t not in ALLOWED_TYPES or t in types:
            continue
        if t == "DRESS" and types & {"TOP", "BOTTOM"}:
            continue
        if t in ("TOP", "BOTTOM") and "DRESS" in types:
            continue
        chosen.append(item["clothesId"])
        types.add(t)
    if not chosen and clothes:
        chosen = [clothes[0]["clothesId"]]   # 조합 불가 후보뿐이면 그대로 보내 앱이 거르게 한다
    return chosen


def embedding(body):
    dims = int(body.get("dimensions") or 1536)
    text = body.get("input")
    text = json.dumps(text, ensure_ascii=False) if not isinstance(text, str) else text
    rnd = random.Random(hashlib.sha256(text.encode("utf-8")).digest())
    vec = [round(rnd.gauss(0, 1), 5) for _ in range(dims)]
    return {"object": "list", "model": body.get("model"),
            "data": [{"object": "embedding", "index": 0, "embedding": vec}],
            "usage": {"prompt_tokens": 0, "total_tokens": 0}}


def respond(stage, body):
    if stage == "condition":
        return function_call("extract_recommendation_condition",
                             {"occasion": "WORK", "styles": ["미니멀"], "categories": [], "keywords": ["깔끔"]})
    if stage == "generation":
        return function_call("select_recommendation_clothes",
                             {"clothesIds": pick_outfit(body),
                              "reason": "모의 응답입니다. 요청과 날씨에 맞춰 겹치지 않게 고른 조합입니다."})
    if stage == "metadata":
        return function_call("analyze_recommendation_clothes_metadata",
                             {"inferredStyles": ["캐주얼"], "formality": "MEDIUM", "occasions": ["DAILY"]})
    return embedding(body)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *args):
        pass

    def send_json(self, code, obj):
        data = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def read_body(self):
        # 앱(JDK HttpClient)은 본문을 chunked 로 보낸다. Content-Length 만 읽으면 빈 본문이 된다
        if "chunked" in (self.headers.get("Transfer-Encoding") or "").lower():
            data = b""
            while True:
                size = int(self.rfile.readline().split(b";")[0].strip() or b"0", 16)
                if size == 0:
                    self.rfile.readline()       # 마지막 CRLF
                    break
                data += self.rfile.read(size)
                self.rfile.readline()           # 청크 뒤 CRLF
            return json.loads(data or b"{}")
        n = int(self.headers.get("Content-Length") or 0)
        return json.loads(self.rfile.read(n) or b"{}")

    def do_GET(self):
        if self.path == "/__stats":
            with lock:
                self.send_json(200, {"config": config, **stats})
            return
        self.send_json(404, {"error": "not found"})

    def do_POST(self):
        global stats
        if self.path == "/__reset":
            with lock:
                stats = fresh_stats()
            self.send_json(200, {"reset": now_iso()})
            return
        if self.path == "/__config":
            body = self.read_body()
            with lock:
                delay = body.get("delay_ms")
                if isinstance(delay, (int, float)):
                    for s in REQUEST_STAGES:
                        config["delay_ms"][s] = int(delay)
                elif isinstance(delay, dict):
                    for s, v in delay.items():
                        if s in config["delay_ms"]:
                            config["delay_ms"][s] = int(v)
                for k in ("jitter_ms", "p500"):
                    if k in body:
                        config[k] = body[k]
                print(f"{now_iso()} CONFIG {json.dumps(config)}", flush=True)
                self.send_json(200, config)
            return

        body = self.read_body()
        if self.headers.get("Authorization") != f"Bearer {API_KEY}":
            with lock:
                stats["unauthorized"] += 1
            print(f"{now_iso()} ALERT unexpected key on {self.path} — 진짜 키가 들어왔을 수 있다", flush=True)
            self.send_json(401, {"error": {"message": "mock: wrong key"}})
            return
        stage = classify(self.path, body)
        with lock:
            st = stats["by_stage"][stage]
            st["received"] += 1
            st["in_flight"] += 1
            st["in_flight_max"] = max(st["in_flight_max"], st["in_flight"])
            stats["in_flight"] += 1
            stats["in_flight_max"] = max(stats["in_flight_max"], stats["in_flight"])
            delay = config["delay_ms"][stage]
            jitter = config["jitter_ms"]
            fail = random.random() < float(config["p500"])
        try:
            wait = delay + (random.uniform(-jitter, jitter) if jitter else 0)
            if wait > 0:
                time.sleep(wait / 1000)
            if stage == "unknown":
                self.send_json(400, {"error": {"message": "mock: unknown tool"}})
            elif fail:
                self.send_json(500, {"error": {"message": "mock: injected 500"}})
            else:
                self.send_json(200, respond(stage, body))
            with lock:
                st["err500" if fail else "ok"] += 1
        except (BrokenPipeError, ConnectionResetError):
            with lock:
                st["client_gone"] += 1     # 앱이 타임아웃으로 먼저 끊었다
        finally:
            with lock:
                st["in_flight"] -= 1
                stats["in_flight"] -= 1


class Server(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 1024


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 18090
    print(f"{now_iso()} mock-openai-v2 listening on {port}", flush=True)
    Server(("0.0.0.0", port), Handler).serve_forever()
