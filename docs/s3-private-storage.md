# 비공개 S3 저장 기반

이 구성은 기존 `ImageStorage`와 가상 피팅 흐름에 연결하지 않은 추가 기반이다. 기본값은
비활성이며 `S3_STORAGE_ENABLED=true`일 때만 S3 클라이언트와 저장 컴포넌트가 생성된다.

## 저장 원칙

- 버킷은 비공개로 유지하고 퍼블릭 액세스 차단을 켠다.
- DB에는 만료되지 않는 객체 키만 저장한다. Presigned URL은 응답 직전에 발급하며 저장하지 않는다.
- 업로드 가능한 형식은 JPEG, PNG, GIF, WebP이고 기본 최대 크기는 10MB다.
- 외부 이미지 저장은 공개 HTTPS 주소만 허용하며 리다이렉트와 내부망 주소를 거부한다.
- 애플리케이션 설정이나 `.env`에 AWS Access Key를 넣지 않는다. 배포 환경은 ECS Task Role,
  로컬 환경은 AWS SDK 기본 자격 증명 체인을 사용한다.

## 환경 변수

```text
S3_STORAGE_ENABLED=false
AWS_REGION=ap-northeast-2
AWS_S3_BUCKET=
S3_PRESIGNED_URL_TTL=15m
S3_MAX_SIZE=10485760
```

활성화 전에 버킷, 리전, IAM 권한을 먼저 준비한다. 기존 이미지 저장 흐름을 전환하는 작업은
별도 변경으로 진행해야 한다. 문제가 생기면 `S3_STORAGE_ENABLED=false`로 되돌리면 기존 동작에
영향 없이 S3 빈 생성만 중단된다.

## 최소 IAM 권한

Task Role에는 사용하는 버킷의 객체에 대해서만 다음 권한을 부여한다. 현재 구현은 버킷 목록을
조회하지 않으므로 `s3:ListBucket`은 필요하지 않다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject"
      ],
      "Resource": "arn:aws:s3:::YOUR_PRIVATE_BUCKET/*"
    }
  ]
}
```

버킷 정책에는 TLS가 아닌 요청을 거부하는 조건을 추가하고, 기본 암호화(SSE-S3 또는 KMS)를
켜는 것을 권장한다. 객체 보존 기간이 정해지면 별도 Lifecycle 규칙으로 정리한다.
