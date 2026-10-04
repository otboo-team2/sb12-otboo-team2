#!/usr/bin/env python3
"""s8 회차 summary.json 들을 모아 비교용 압축 JSON 을 표준출력으로 낸다.

usage: combine.py <label-prefix>...     예) combine.py slow- stress- normal- fault-
"""
import glob
import json
import os
import sys

KEEP = ["step", "target_rps", "ai_total", "ai_arrival_rps", "ai_success", "ai_fallback", "ai_error",
        "ai_success_rate", "ai_success_rps", "ai_p50", "ai_p95", "ai_p99", "ai_success_p50",
        "ai_success_p95", "ai_success_p99", "ai_fallback_p50", "ai_fallback_p95", "feed_total",
        "feed_p50", "feed_p95", "feed_p99", "feed_error_rate", "dropped_ai", "srv_ai_in_flight_max",
        "srv_threads_live_max", "srv_heap_used_max_mb", "srv_cpu_avg", "srv_cpu_max", "srv_gc_pause_ms",
        "srv_hikari_active_max", "srv_hikari_pending_max", "srv_tomcat_busy_max", "srv_http_active_max",
        "srv_rejected_delta"]
runs = []
for prefix in sys.argv[1:]:
    for d in sorted(glob.glob(f"/opt/k6/results/s8/{prefix}*")):
        f = os.path.join(d, "summary.json")
        if not os.path.exists(f):
            continue
        s = json.load(open(f))
        m = s["meta"]
        mock = s.get("mock") or {}
        runs.append({
            "dir": os.path.basename(d), "label": m["label"], "cond": m["cond"], "thread": m["thread"],
            "cap": m["cap"], "delay": m["mock_config"]["delay_ms"]["condition"], "started": m["started"],
            "steps": [[st.get(k) for k in KEEP] for st in s["steps"]],
            "outcomes": s.get("server_outcomes_total"),
            "mock_received": {k: v["received"] for k, v in (mock.get("by_stage") or {}).items() if v["received"]},
            "mock_client_gone": {k: v["client_gone"] for k, v in (mock.get("by_stage") or {}).items() if v["client_gone"]},
            "mock_unauthorized": mock.get("unauthorized"),
            "dropped": s.get("dropped_total"),
        })
print(json.dumps({"keys": KEEP, "runs": runs}, ensure_ascii=False, separators=(",", ":")))
