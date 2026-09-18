# 개인정보처리방침 · Privacy Policy

**otboo** — 날씨 기반 개인 맞춤 의상 추천 서비스
최종 수정일: 2026-09-18

> otboo 는 코드잇 부트캠프 파이널 프로젝트로 만든 학습용 서비스입니다. 상업적으로 운영하지 않으며,
> 수집한 정보를 판매하거나 광고에 사용하지 않습니다.

---

## 1. 수집하는 정보

| 구분 | 항목 | 수집 시점 |
|---|---|---|
| 계정 | 이메일, 이름, 비밀번호(해시로만 저장) | 회원가입 |
| 프로필 | 성별, 생년월일, 프로필 이미지 | 사용자가 직접 입력할 때 |
| 위치 | 위도·경도, 행정구역 | 날씨 예보를 받기 위해 사용자가 위치를 설정할 때 |
| 취향 | 온도 민감도, 스타일 선호 | 사용자가 직접 설정할 때 |
| 콘텐츠 | 등록한 의상 정보·이미지, 작성한 피드·댓글, 주고받은 메시지 | 사용자가 작성할 때 |

비밀번호는 해시로만 저장하며 원문을 보관하지 않습니다.

## 2. 이용 목적

- 날씨와 사용자 취향에 맞는 의상 추천
- 피드·댓글·팔로우·메시지 등 서비스 기능 제공
- 알림 발송

위치 정보는 해당 지역의 날씨 예보를 받아오는 데만 사용합니다.

## 3. 제3자 서비스

서비스 기능을 위해 아래 외부 API 를 호출합니다.

| 서비스 | 보내는 정보 | 용도 |
|---|---|---|
| OpenWeatherMap | 위도·경도 | 날씨 예보 조회 |
| Kakao | 위도·경도 | 좌표를 행정구역 이름으로 변환 |
| Pinterest | 없음 (읽기만 함 — 아래 5장 참고) | 코디 참고 이미지 수집 |
| Google Gemini | 사용자가 등록한 의상 이미지·상품 페이지 내용 | 의상 정보 자동 입력 |
| FASHN | 사용자가 등록한 인물 사진·의상 이미지 | 가상 피팅 이미지 생성 |

가상 피팅과 의상 자동 입력은 사용자가 그 기능을 직접 실행할 때만 동작하며, 실행하지 않으면
사진이 외부로 나가지 않습니다.

## 4. 보관 기간

회원 탈퇴 시 계정 정보와 사용자가 작성한 콘텐츠를 삭제합니다. 날씨 데이터는 수집 후 7일이 지나면
배치가 자동으로 지웁니다.

## 5. Pinterest 데이터 처리

otboo 는 **otboo 팀 계정이 소유하거나 멤버로 참여한 보드**의 핀만 읽습니다. Pinterest 전체를
검색하지 않으며, 다른 Pinterest 사용자의 계정·보드·개인정보에 접근하지 않습니다.

저장하는 값은 핀 ID, 보드 ID, 이미지 주소, 원본 핀 링크, 제목, 설명, 설명에서 읽어낸 태그(기온
구간·날씨·스타일 등), 동기화 시각입니다. **이미지 파일 자체는 복사하거나 우리 서버에 보관하지
않고 Pinterest 주소를 그대로 참조**하며, 각 이미지는 원본 핀으로 연결됩니다.

otboo 사용자의 개인정보는 Pinterest 로 전송되지 않습니다.

## 6. 문의

GitHub 이슈로 문의해 주세요 — https://github.com/otboo-team2/sb12-otboo-team2/issues

---

# Privacy Policy (English)

**otboo** — a weather-based outfit recommendation service
Last updated: 2026-09-18

> otboo is a student final project built for the Codeit bootcamp. It is not operated commercially.
> We do not sell collected information or use it for advertising.

## 1. Information we collect

| Category | Items | When |
|---|---|---|
| Account | Email, name, password (stored only as a hash) | Sign-up |
| Profile | Gender, date of birth, profile image | When the user enters them |
| Location | Latitude/longitude, administrative region | When the user sets a location to receive a forecast |
| Preferences | Temperature sensitivity, style preferences | When the user sets them |
| Content | Clothing items and images, feed posts, comments, direct messages | When the user creates them |

## 2. How we use it

To recommend outfits based on weather and user preferences, to provide feed, comment, follow and
messaging features, and to send notifications. Location is used only to fetch the weather forecast
for that area.

## 3. Third-party services

| Service | What we send | Purpose |
|---|---|---|
| OpenWeatherMap | Latitude/longitude | Weather forecast |
| Kakao | Latitude/longitude | Convert coordinates to a region name |
| Pinterest | Nothing (read-only — see section 5) | Collect outfit reference images |
| Google Gemini | Clothing images and product page content the user submits | Auto-fill clothing details |
| FASHN | Person and clothing images the user submits | Generate virtual try-on images |

Virtual try-on and auto-fill run only when the user explicitly invokes them.

## 4. Retention

Account information and user-created content are deleted when the user deletes their account.
Weather data is purged automatically by a batch job seven days after collection.

## 5. Pinterest data

otboo reads pins **only from boards that the otboo team account owns or is a member of**. We do not
search Pinterest globally, and we do not access any other Pinterest user's account, boards or
personal information.

We store the pin id, board id, image URL, original pin link, title, description, tags parsed from
that description (temperature range, sky condition, style and so on), and the time of synchronization.
**We do not copy or re-host the image files themselves** — we reference the Pinterest image URL, and
every image links back to its original pin.

No otboo user's personal information is sent to Pinterest.

## 6. Contact

Please open a GitHub issue — https://github.com/otboo-team2/sb12-otboo-team2/issues
