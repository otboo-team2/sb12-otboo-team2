# syntax=docker/dockerfile:1
#
# 프론트 정적 파일 + 리버스 프록시.
#
# 왜 nginx 가 API 까지 받아 넘기는가 — 프론트와 API 가 다른 오리진이면
# 리프레시 토큰 쿠키(SameSite=Lax)가 따라가지 않아 재발급이 깨지고,
# 구글 OAuth 콜백도 오리진이 갈려 돌아올 곳을 잃는다.
# 하나의 오리진으로 묶으면 로컬 vite 프록시와 같은 구조가 된다.

FROM node:22-alpine AS builder
WORKDIR /app

# pnpm 버전은 package.json 의 packageManager 가 정한다. corepack 이 그 값을 읽는다.
RUN corepack enable

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
# tsc -b && vite build. 타입 오류도 여기서 걸린다.
RUN pnpm build

FROM nginx:1.27-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html
EXPOSE 80
