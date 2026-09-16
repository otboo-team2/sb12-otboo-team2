# 공식 이미지에는 한국어 형태소 분석기가 없다. nori 를 넣은 이미지를 직접 굽는다.
#
# ⚠️ 태그를 올릴 때는 Spring Boot 가 관리하는 elasticsearch-java 버전과 맞춰야 한다.
#    (Boot 3.5.16 -> co.elastic.clients:elasticsearch-java:8.18.8)
FROM docker.elastic.co/elasticsearch/elasticsearch:8.18.8

RUN bin/elasticsearch-plugin install --batch analysis-nori
