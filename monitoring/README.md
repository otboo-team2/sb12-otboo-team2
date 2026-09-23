# 관측 (Prometheus + Grafana)

## 쓰는 법

```bash
docker compose up -d          # mysql · redis · prometheus · grafana
./gradlew :app-api:bootRun
```

| | 주소 | 비고 |
|---|---|---|
| Grafana | http://localhost:3000 | 로그인 없음(로컬 전용). 열면 개요 대시보드가 바로 뜬다 |
| Prometheus | http://localhost:9090 | 쿼리를 직접 짜볼 때 |
| 수집 대상 확인 | http://localhost:9090/targets | `otboo-api` 가 **UP** 이어야 한다 |

앱이 안 잡히면 `/targets` 에서 `otboo-api` 가 DOWN 이다. 보통 앱이 안 떠 있거나
8080 이 아닌 포트로 떠 있는 경우다.

## 구성

```
monitoring/
├── prometheus/prometheus.yml            수집 대상 (host.docker.internal:8080)
└── grafana/
    ├── provisioning/datasources/        데이터소스 자동 등록
    ├── provisioning/dashboards/         대시보드 자동 등록 설정
    └── dashboards/otboo-overview.json   개요 대시보드 (패널 4개)
```

**대시보드는 파일이 원본이다.** UI 에서 고쳐도 재시작하면 파일 내용으로 돌아간다.
바꾸려면 UI 에서 만든 뒤 `Dashboard settings → JSON Model` 을 복사해서 파일에 덮어쓰고 커밋한다.
그래야 팀원 전부가 같은 대시보드를 본다.

## 개요 대시보드 패널

| 패널 | 무엇을 보나 |
|---|---|
| 요청 처리율 | 엔드포인트별 초당 요청 수. 부하 테스트의 기준선 |
| 응답 시간 p95 | 평균이 아니라 p95. 평균은 느린 요청을 빠른 요청 다수가 가린다 |
| 5xx 응답 수 | 0 이 정상. 올라오면 그 시각의 `requestId` 로 로그를 찾는다 |
| JVM 힙 | 톱니 모양이면 정상(GC). 계속 우상향이면 참조를 안 놓고 있다 |

## 자기 파트 지표 추가하기

`@Timed` 나 `MeterRegistry` 로 지표를 만들면 별도 설정 없이 Prometheus 로 나간다.

```java
private final MeterRegistry meterRegistry;

meterRegistry.counter("otboo_recommendation_requested",
        "source", "llm").increment();
```

이름은 `otboo_` 로 시작하고 소문자 + 밑줄을 쓴다. 우리 지표와 프레임워크 지표를 구분하기 위해서다.

⚠️ **라벨에 사용자 id 나 피드 id 같은 값을 넣지 않는다.** 값 하나마다 시계열이 하나씩
생겨서 Prometheus 메모리가 터진다(cardinality explosion). 라벨은 종류가 유한한 것만 —
`source=llm|cache`, `result=ok|fail` 같은 것.

## 아직 안 한 것

`app-batch` 는 수집 대상에 없다. 단독 실행 후 종료돼서 Prometheus 가 긁어갈 시점에
살아 있지 않기 때문이다. Pushgateway 가 필요하다.
