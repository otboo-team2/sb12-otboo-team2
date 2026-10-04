// 시나리오 8 — AI 추천 동시 상한 × 요청 스레드(플랫폼/가상) 비교.
//
// 질문: 피드 응답을 지키면서 "실제 AI 결과를 받은 요청"을 늘릴 수 있는가.
//       상한만 올려도 되는가, 가상 스레드가 추가로 필요한가.
// 방법: 가짜 OpenAI(mock-openai-v2.py) 지연을 고정하고, 배경 피드 + AI 추천 도착률을 단계별로 고정한다.
//       AI 응답은 HTTP 200 이어도 reason 이 있어야 AI 성공이다(기본 추천은 reason 이 없다).
//
// 환경변수
//   BASE          실험 태스크 직접 주소 (http://<ip>:8080). ALB 를 거치지 않는다
//   AI_STEPS      단계별 AI 도착률(rps, 소수 가능) 예) 0.1,1,2,4
//   STEP_DUR      단계 길이(초)          WARMUP  워밍업 길이(초)   WARMUP_AI  워밍업 AI rps
//   DRAIN         마지막에 진행 중 요청을 기다리는 시간(초) — gracefulStop
//   FEED_RPS      배경 피드 rps          USERS   로그인 계정 수(loadtest0000~)
//   WEATHER_ID    고정 날씨 id (회차 간 같은 후보를 쓰게)
//   PROMPT_MODE   unique(캐시 미적중, 기본) | fixed(캐시 적중 실험)
//   RUN           회차 라벨 — 프롬프트에 넣어 회차 사이 캐시 공유를 막는다
import http from 'k6/http';
import exec from 'k6/execution';
import { Trend } from 'k6/metrics';
import { login, auth, csrf, BASE } from './lib.js';

const AI_STEPS = (__ENV.AI_STEPS || '0.1,1,2,4').split(',').map(Number);
const STEP_DUR = Number(__ENV.STEP_DUR || 120);
const WARMUP = Number(__ENV.WARMUP || 60);
const WARMUP_AI = Number(__ENV.WARMUP_AI || 0.2);
const DRAIN = Number(__ENV.DRAIN || 60);
const FEED_RPS = Number(__ENV.FEED_RPS || 5);
const USERS = Number(__ENV.USERS || 50);
const PROMPT_MODE = __ENV.PROMPT_MODE || 'unique';
const RUN = __ENV.RUN || 'run';
const TOTAL = WARMUP + AI_STEPS.length * STEP_DUR;

const feedMs = new Trend('feed_ms', true);
const aiMs = new Trend('ai_ms', true);

// rps(소수) → 분당 정수 도착률. constant-arrival-rate 의 rate 는 정수여야 한다
function perMinute(rps) { return Math.max(1, Math.round(rps * 60)); }
function aiScenario(rps, start, duration, step, graceful) {
  return {
    executor: 'constant-arrival-rate', exec: 'ai',
    rate: perMinute(rps), timeUnit: '1m', duration: `${duration}s`, startTime: `${start}s`,
    // 요청 하나가 최대 ~40 s(12 s × 3 단계 + α). 그만큼 VU 를 미리 잡아 생성기 쪽 지연을 막는다
    preAllocatedVUs: Math.ceil(rps * 45) + 5, maxVUs: Math.ceil(rps * 130) + 20,
    gracefulStop: `${graceful}s`, tags: { step },
  };
}

const scenarios = {
  feeds: {
    executor: 'constant-arrival-rate', exec: 'feeds',
    rate: FEED_RPS, timeUnit: '1s', duration: `${TOTAL}s`,
    preAllocatedVUs: 30, maxVUs: 400, gracefulStop: `${DRAIN}s`,
  },
  ai_warmup: aiScenario(WARMUP_AI, 0, WARMUP, 'warmup', 0),
};
AI_STEPS.forEach((rps, i) => {
  // 단계가 끝나도 진행 중인 요청은 DRAIN 만큼 기다린다(잘리면 성공률이 왜곡된다). 결과는 시작한 단계로 센다
  scenarios[`ai_s${i + 1}`] = aiScenario(rps, WARMUP + i * STEP_DUR, STEP_DUR, `s${i + 1}_${rps}rps`, DRAIN);
});

export const options = {
  setupTimeout: '180s',
  summaryTrendStats: ['count', 'avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
  scenarios,
};

export function setup() {
  const sessions = [];
  for (let i = 0; i < USERS; i++) sessions.push(login(i));
  let weatherId = __ENV.WEATHER_ID;
  if (!weatherId) {
    const w = http.get(`${BASE}/api/weathers?latitude=37.5665&longitude=126.978`, auth(sessions[0]));
    if (w.status !== 200 || !w.json().length) throw new Error(`weather ${w.status}: ${String(w.body).slice(0, 160)}`);
    weatherId = w.json()[0].id;
  }
  return { sessions, weatherId, t0: Date.now() };
}

// 피드는 시나리오 하나로 계속 흘리므로, 시각으로 어느 단계인지 붙인다
function stepAt(t0) {
  const sec = (Date.now() - t0) / 1000;
  if (sec < WARMUP) return 'warmup';
  const i = Math.min(AI_STEPS.length - 1, Math.floor((sec - WARMUP) / STEP_DUR));
  return `s${i + 1}_${AI_STEPS[i]}rps`;
}

export function feeds(d) {
  const s = d.sessions[exec.vu.idInTest % d.sessions.length];
  const step = stepAt(d.t0);
  const r = http.get(`${BASE}/api/feeds?limit=20&sortBy=createdAt&sortDirection=DESCENDING`,
    { ...auth(s), tags: { name: 'feeds', step }, timeout: '120s' });
  feedMs.add(r.timings.duration, { step, ok: String(r.status === 200) });
}

// POST 는 Bearer 가 있어도 CSRF 헤더가 필요하다. 매 요청 지금 쿠키 값을 읽어 보낸다(s7 과 같다)
function currentXsrf() {
  const jar = (http.cookieJar().cookiesForURL(BASE)['XSRF-TOKEN'] || [])[0];
  if (jar) return jar;
  csrf();
  return (http.cookieJar().cookiesForURL(BASE)['XSRF-TOKEN'] || [])[0];
}

export function ai(d) {
  const s = d.sessions[exec.vu.idInTest % d.sessions.length];
  const n = exec.scenario.name.slice('ai_s'.length);
  const step = exec.scenario.name === 'ai_warmup' ? 'warmup' : `s${n}_${AI_STEPS[Number(n) - 1]}rps`;
  const xsrf = currentXsrf();
  // 조건·임베딩이 프롬프트 기준 30분 캐시된다. unique 는 매번 다른 문장(캐시 미적중), fixed 는 같은 문장
  const prompt = PROMPT_MODE === 'fixed'
    ? '출근할 때 입을 깔끔한 코디 추천해줘'
    : `출근할 때 입을 깔끔한 코디 추천해줘 ${RUN}-${exec.scenario.iterationInTest}-${exec.vu.idInTest}`.slice(0, 100);
  const r = http.post(`${BASE}/api/recommendations/ai`,
    JSON.stringify({ weatherId: d.weatherId, prompt }),
    { headers: { ...auth(s).headers, 'Content-Type': 'application/json', 'X-XSRF-TOKEN': xsrf },
      tags: { name: 'ai', step }, timeout: '120s' });
  let result = 'error';
  if (r.status === 200) {
    let body = null;
    try { body = r.json(); } catch (e) { body = null; }
    result = body && typeof body.reason === 'string' && body.reason.length > 0 ? 'success' : 'fallback';
  }
  aiMs.add(r.timings.duration, { step, result, status: String(r.status) });
}
