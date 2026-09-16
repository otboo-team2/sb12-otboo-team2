# syntax=docker/dockerfile:1
#
# 백엔드 앱 3개(app-api · app-realtime · app-batch)의 공통 이미지 빌드.
# 어느 모듈을 담을지는 MODULE 로 받는다.
#
#   docker build --build-arg MODULE=app-api -t otboo-api .
#
# 파일을 셋으로 나누지 않은 이유 — 빌드 단계가 완전히 같다. 셋으로 두면
# 한 곳만 고치고 나머지를 잊는다. 또 빌드 스테이지가 같으면 두 번째·세 번째
# 이미지는 레이어 캐시를 그대로 맞고 지나간다(그래서 MODULE 을 맨 뒤에서만 쓴다).

# ─────────────────── 1. 빌드 ───────────────────
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

# 빌드 스크립트를 먼저 복사해 의존성만 내려받는다.
# 소스만 바뀐 커밋에서는 이 레이어가 캐시에 맞아 다운로드를 건너뛴다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY common-core/build.gradle common-core/
COPY app-api/build.gradle app-api/
COPY app-realtime/build.gradle app-realtime/
COPY app-batch/build.gradle app-batch/
RUN chmod +x gradlew \
    && ./gradlew --no-daemon -q \
        :app-api:dependencies :app-realtime:dependencies :app-batch:dependencies \
        --configuration runtimeClasspath

COPY common-core common-core
COPY app-api app-api
COPY app-realtime app-realtime
COPY app-batch app-batch

# 테스트는 돌리지 않는다. CI 가 이미 돌렸고, 여기서 돌리면 Testcontainers 때문에
# 빌드 컨테이너 안에서 또 도커가 필요해진다.
RUN ./gradlew --no-daemon -x test \
        :app-api:bootJar :app-realtime:bootJar :app-batch:bootJar

# ─────────────────── 2. 레이어 분해 ───────────────────
# 실행 가능 jar 하나는 100MB 에 가깝다. 그대로 COPY 하면 코드 한 줄만 고쳐도
# 100MB 를 통째로 다시 올린다. 라이브러리와 애플리케이션을 갈라두면
# 라이브러리 레이어는 의존성이 바뀔 때만 다시 올라간다.
FROM builder AS extractor
ARG MODULE
WORKDIR /extracted
RUN java -Djarmode=tools -jar /workspace/${MODULE}/build/libs/*-SNAPSHOT.jar \
        extract --layers --launcher --destination .

# ─────────────────── 3. 실행 ───────────────────
FROM eclipse-temurin:21-jre-jammy AS runtime

# root 로 돌리지 않는다. 컨테이너가 뚫렸을 때 할 수 있는 일을 줄인다.
RUN useradd --system --create-home --uid 10001 otboo
USER otboo
WORKDIR /app

# 잘 안 바뀌는 것부터 복사한다. 순서가 곧 캐시 적중률이다.
COPY --from=extractor --chown=otboo:otboo /extracted/dependencies/ ./
COPY --from=extractor --chown=otboo:otboo /extracted/spring-boot-loader/ ./
COPY --from=extractor --chown=otboo:otboo /extracted/snapshot-dependencies/ ./
COPY --from=extractor --chown=otboo:otboo /extracted/application/ ./

# UTC 통일 5지점 중 둘(컨테이너 TZ · JVM). 나머지 셋은 MySQL 서버·JDBC·하이버네이트에 있다.
ENV TZ=UTC
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=UTC -XX:MaxRAMPercentage=75.0"

# 포트는 이미지마다 다르고(api 8080 / realtime 8081 / batch 없음) compose 가 정한다.
# HEALTHCHECK 도 두지 않는다 — jre 이미지에 curl 이 없고, 검사 주체는 compose·LB 다.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
