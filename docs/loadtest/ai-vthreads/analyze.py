#!/usr/bin/env python3
"""s8 회차 디렉터리 하나를 단계별로 집계한다.

입력: meta.json, k6.csv.gz(k6 --out csv), scrape.txt(scrape.sh), mock-stats.json, summary-export.json
출력: summary.json(기계용), 표준출력 markdown(사람용)
"""
import csv
import gzip
import json
import math
import re
import sys
from collections import defaultdict

D = sys.argv[1]
meta = json.load(open(f"{D}/meta.json"))
STEPS = [float(x) for x in meta["AI_STEPS"].split(",")]
STEP_DUR = float(meta["STEP_DUR"])
WARMUP = float(meta["WARMUP"])
step_names = [f"s{i + 1}_{meta['AI_STEPS'].split(',')[i]}rps" for i in range(len(STEPS))]


def pct(values, p):
    if not values:
        return None
    v = sorted(values)
    k = (len(v) - 1) * p / 100
    lo, hi = math.floor(k), math.ceil(k)
    return v[lo] + (v[hi] - v[lo]) * (k - lo)


def tags(extra):
    return dict(kv.split("=", 1) for kv in extra.split("&") if "=" in kv) if extra else {}


ai = defaultdict(lambda: defaultdict(list))      # step -> result -> [ms]
feed = defaultdict(lambda: {"ok": [], "fail": 0})
dropped = defaultdict(float)
first_ts = None
with gzip.open(f"{D}/k6.csv.gz", "rt") as f:
    for row in csv.DictReader(f):
        m = row["metric_name"]
        if m == "ai_ms":
            t = tags(row["extra_tags"])
            ai[t.get("step")][t.get("result")].append(float(row["metric_value"]))
        elif m == "feed_ms":
            t = tags(row["extra_tags"])
            ts = float(row["timestamp"])
            first_ts = ts if first_ts is None else min(first_ts, ts)
            if t.get("ok") == "true":
                feed[t.get("step")]["ok"].append(float(row["metric_value"]))
            else:
                feed[t.get("step")]["fail"] += 1
        elif m == "dropped_iterations":
            dropped[row["scenario"]] += float(row["metric_value"])

# ── 서버 지표: "ts name{labels} value"
series = defaultdict(list)      # (name, labels) -> [(ts, v)]
line = re.compile(r"^(\d+) ([a-zA-Z_:][\w:]*)(\{[^}]*\})? (\S+)")
try:
    for raw in open(f"{D}/scrape.txt"):
        mt = line.match(raw)
        if mt:
            v = float(mt.group(4))
            if not math.isnan(v):
                series[(mt.group(2), mt.group(3) or "")].append((int(mt.group(1)), v))
except FileNotFoundError:
    pass


def window(i):
    start = first_ts + WARMUP + i * STEP_DUR
    return start, start + STEP_DUR


def sum_by_ts(name, label_filter=""):
    agg = defaultdict(float)
    for (n, lab), pts in series.items():
        if n == name and label_filter in lab:
            for ts, v in pts:
                agg[ts] += v
    return sorted(agg.items())


def stat(name, lo, hi, fn, label_filter=""):
    vals = [v for ts, v in sum_by_ts(name, label_filter) if lo <= ts < hi]
    return fn(vals) if vals else None


def delta(name, lo, hi, label_filter=""):
    pts = [(ts, v) for ts, v in sum_by_ts(name, label_filter) if lo - 5 <= ts <= hi + 5]
    return pts[-1][1] - pts[0][1] if len(pts) >= 2 else None


def r(x, nd=1):
    return None if x is None else round(x, nd)


out = {"meta": meta, "steps": []}
for i, name in enumerate(step_names):
    res = ai.get(name, {})
    succ, fb, err = res.get("success", []), res.get("fallback", []), res.get("error", [])
    allv = succ + fb + err
    n = len(allv)
    fd = feed.get(name, {"ok": [], "fail": 0})
    fn = len(fd["ok"]) + fd["fail"]
    s = {"step": name, "target_rps": STEPS[i], "ai_total": n,
         "ai_arrival_rps": r(n / STEP_DUR, 3),
         "ai_success": len(succ), "ai_fallback": len(fb), "ai_error": len(err),
         "ai_success_rate": r(100 * len(succ) / n if n else None),
         "ai_success_rps": r(len(succ) / STEP_DUR, 3),
         "ai_p50": r(pct(allv, 50)), "ai_p95": r(pct(allv, 95)), "ai_p99": r(pct(allv, 99)),
         "ai_success_p50": r(pct(succ, 50)), "ai_success_p95": r(pct(succ, 95)), "ai_success_p99": r(pct(succ, 99)),
         "ai_fallback_p50": r(pct(fb, 50)), "ai_fallback_p95": r(pct(fb, 95)),
         "feed_total": fn, "feed_p50": r(pct(fd["ok"], 50)), "feed_p95": r(pct(fd["ok"], 95)),
         "feed_p99": r(pct(fd["ok"], 99)), "feed_error_rate": r(100 * fd["fail"] / fn if fn else None, 2),
         "dropped_ai": dropped.get(f"ai_s{i + 1}", 0)}
    if first_ts is not None and series:
        lo, hi = window(i)
        s.update({
            "srv_ai_in_flight_max": stat("otboo_recommendation_ai_in_flight", lo, hi, max),
            "srv_threads_live_max": stat("jvm_threads_live_threads", lo, hi, max),
            "srv_heap_used_max_mb": r((stat("jvm_memory_used_bytes", lo, hi, max, 'area="heap"') or 0) / 1048576),
            "srv_cpu_avg": r(stat("process_cpu_usage", lo, hi, lambda v: sum(v) / len(v)), 3),
            "srv_cpu_max": r(stat("process_cpu_usage", lo, hi, max), 3),
            "srv_gc_pause_ms": r((delta("jvm_gc_pause_seconds_sum", lo, hi) or 0) * 1000),
            "srv_hikari_active_max": stat("hikaricp_connections_active", lo, hi, max),
            "srv_hikari_pending_max": stat("hikaricp_connections_pending", lo, hi, max),
            "srv_tomcat_busy_max": stat("tomcat_threads_busy_threads", lo, hi, max),
            "srv_http_active_max": stat("http_server_requests_active_seconds_gcount", lo, hi, max),
            "srv_rejected_delta": delta("otboo_recommendation_ai_rejected_total", lo, hi),
        })
    out["steps"].append(s)

# 서버가 센 결과·사유(회차 전체, 워밍업 포함)
reasons = {}
for (n, lab), pts in series.items():
    if n == "otboo_recommendation_ai_duration_seconds_count" and pts:
        o = re.search(r'outcome="([^"]+)"', lab).group(1)
        rs = re.search(r'reason="([^"]+)"', lab).group(1)
        reasons[f"{o}/{rs}"] = pts[-1][1]
out["server_outcomes_total"] = reasons
out["dropped_total"] = dict(dropped)
try:
    out["mock"] = json.load(open(f"{D}/mock-stats.json"))
except Exception:
    out["mock"] = None
json.dump(out, open(f"{D}/summary.json", "w"), ensure_ascii=False, indent=1)

# ── markdown
print(f"## {meta.get('label')}  ({meta.get('cond')})")
print(f"thread={meta.get('thread')} cap={meta.get('cap')} mock={meta.get('mock_config')} steps={meta['AI_STEPS']} step={STEP_DUR}s")
cols = ["step", "ai_total", "ai_arrival_rps", "ai_success", "ai_fallback", "ai_error", "ai_success_rate",
        "ai_success_rps", "ai_success_p50", "ai_success_p95", "ai_fallback_p95", "ai_p95", "ai_p99",
        "feed_p95", "feed_p99", "feed_error_rate", "dropped_ai", "srv_ai_in_flight_max",
        "srv_threads_live_max", "srv_heap_used_max_mb", "srv_cpu_avg", "srv_hikari_pending_max",
        "srv_tomcat_busy_max", "srv_http_active_max", "srv_rejected_delta"]
print("| " + " | ".join(cols) + " |")
print("|" + "---|" * len(cols))
for s in out["steps"]:
    print("| " + " | ".join(str(s.get(c)) for c in cols) + " |")
print(f"\nserver outcomes: {reasons}")
if out["mock"]:
    print("mock received: " + str({k: v["received"] for k, v in out["mock"]["by_stage"].items() if v["received"]})
          + f" client_gone: { {k: v['client_gone'] for k, v in out['mock']['by_stage'].items() if v['client_gone']} }"
          + f" unauthorized: {out['mock']['unauthorized']}")
print(f"dropped: {dict(dropped)}")
