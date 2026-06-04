# 프론트엔드 API 다국어(i18n) 연동 가이드

백엔드(Mocktalk API)는 **에러 응답과 요청 검증(validation) 메시지**만 locale에 따라 바뀝니다.  
화면 UI 문구(버튼, 메뉴, 레이아웃)는 **프론트엔드 i18n**으로 계속 관리하면 됩니다.

---

## 1. 범위 요약

| 구분 | 서버 locale 적용 | 프론트 담당 |
|------|------------------|-------------|
| `error.reason` | ✅ | 표시 시 API 값 사용 |
| `error.details.fieldErrors[].message` | ✅ | 폼 에러 표시 |
| `error.details.violations[].message` (쿼리 파라미터 검증) | ✅ | 동일 |
| `error.code` | ❌ (항상 동일 문자열) | 분기·로깅·재시도 로직 |
| `data` 본문(게시글, 댓글, 알림 텍스트 등 UGC) | ❌ | 그대로 표시 |
| 게시글 import 검증 `errors`/`warnings` 등 전용 API | ❌ (현재 한국어 위주) | 별도 처리 또는 추후 백엔드 i18n |

---

## 2. 요청: `Accept-Language` 헤더

모든 REST API 호출에 사용자 언어를 넣어 주세요.

```http
Accept-Language: ko
```

| 값 | 동작 |
|----|------|
| 생략 또는 `ko` | 한국어 (서버 **기본값**) |
| `en` | 영어 |
| 그 외 | 한국어로 fallback (`AcceptHeaderLocaleResolver`) |

### 권장 구현

1. 앱 설정/브라우저 언어 → `ko` | `en` 만 매핑 (지원 외 언어는 `ko`).
2. API 클라이언트(axios, fetch wrapper 등) **공통 interceptor**에서 헤더 일괄 설정.
3. 언어 변경 시 이후 요청부터 새 헤더 적용 (이미 받은 `reason`은 재요청 전까지 그대로).

```typescript
// 예: axios
api.interceptors.request.use((config) => {
  const locale = getUserLocale(); // 'ko' | 'en'
  config.headers['Accept-Language'] = locale;
  return config;
});
```

```typescript
// 예: fetch
const headers = {
  'Content-Type': 'application/json',
  'Accept-Language': locale,
  ...(token ? { Authorization: `Bearer ${token}` } : {}),
};
```

---

## 3. 응답 envelope

성공/실패 모두 동일한 래퍼를 사용합니다.

```json
{
  "success": true,
  "data": { },
  "error": null
}
```

실패 시:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON_400",
    "reason": "잘못된 요청입니다.",
    "path": "/api/articles",
    "timestamp": "2026-06-05T12:00:00+09:00",
    "details": { }
  }
}
```

| 필드 | 설명 |
|------|------|
| `code` | **locale 무관**. 로직 분기용 (`AUTH_001`, `ARTICLE_404`, …) |
| `reason` | **locale 적용**. 사용자에게 보여 줄 요약 메시지 |
| `path` | 요청 URI |
| `timestamp` | ISO-8601 |
| `details` | 선택. 검증 상세 등 |

HTTP status는 `error.code`에 대응하는 상태 코드와 맞춰 내려갑니다 (예: `ARTICLE_404` → 404).

---

## 4. 에러 처리 패턴 (권장)

### 표시

- 토스트/다이얼로그/인라인 요약: **`error.reason`**
- code별로 문구를 **프론트에 또 정의하지 않는 것**을 권장합니다 (이중 번역·불일치 방지).

### 분기 (예)

| `error.code` | 프론트 동작 예시 |
|--------------|------------------|
| `AUTH_001`, `AUTH_013`, `COMMON_401` | 로그인 화면으로 이동 |
| `COMMON_403` | 권한 없음 안내 (문구는 `reason`) |
| `USER_028`, `USER_021` | 해당 필드 하이라이트 |
| `ARTICLE_404` | 목록으로 back |

```typescript
function handleApiError(error: ApiError, httpStatus: number) {
  switch (error.code) {
    case 'AUTH_001':
    case 'COMMON_401':
      redirectToLogin();
      break;
    case 'COMMON_403':
      showToast(error.reason);
      break;
    default:
      showToast(error.reason);
  }
}
```

### 403 주의

Spring Security `AccessDeniedException` 경로도 응답은 **`COMMON_403` + locale별 `reason`** 만 내려갑니다.  
프론트에만 있는 “게시글 조회 권한이 없습니다” 같은 **고정 한글 문구**와 API `reason`을 동시에 쓰지 마세요.

---

## 5. Validation (400) 응답

`@Valid` 실패 시:

- `error.code`: `COMMON_400`
- `error.reason`: 공통 문구 (예: ko `잘못된 요청입니다.`, en `Invalid request.`)
- `error.details.fieldErrors`: 필드별 메시지 (**locale 적용**)

```json
{
  "success": false,
  "error": {
    "code": "COMMON_400",
    "reason": "잘못된 요청입니다.",
    "path": "/api/auth/login",
    "timestamp": "...",
    "details": {
      "fieldErrors": [
        { "field": "loginId", "message": "필수 항목입니다." },
        { "field": "password", "message": "필수 항목입니다." }
      ]
    }
  }
}
```

`Accept-Language: en` 예:

```json
"fieldErrors": [
  { "field": "loginId", "message": "must not be blank" },
  { "field": "password", "message": "must not be blank" }
]
```

쿼리 파라미터 `@Min` 등 제약 위반 시 키는 `violations` 입니다.

```json
"details": {
  "violations": [
    { "field": "count", "message": "..." }
  ]
}
```

폼 바인딩: `field` 이름을 DTO 프로퍼티명과 맞춰 매핑하면 됩니다.

---

## 6. 예시: 동일 code, 다른 reason

**요청**

```http
GET /api/... 
Accept-Language: ko
```

```json
{
  "success": false,
  "error": {
    "code": "COMMON_401",
    "reason": "인증이 필요합니다."
  }
}
```

**요청**

```http
Accept-Language: en
```

```json
{
  "error": {
    "code": "COMMON_401",
    "reason": "Authentication required."
  }
}
```

`code`는 동일, `reason`만 변경됩니다.

---

## 7. 도메인별 `error.code` 참고

전체 목록은 백엔드 [`ErrorCode.java`](../src/main/java/com/mocktalkback/global/common/dto/ErrorCode.java) 및 `messages_ko.properties` / `messages_en.properties`를 참고하세요.

자주 쓰는 prefix:

| Prefix | 영역 |
|--------|------|
| `COMMON_*` | 공통 HTTP/검증 |
| `AUTH_*`, `OAUTH2_*` (일부) | 인증 |
| `USER_*` | 회원 |
| `BOARD_*` | 게시판 |
| `ARTICLE_*` | 게시글 |
| `COMMENT_*` | 댓글 |
| `FILE_*`, `UPLOAD_*` | 파일/업로드 |
| `MOD_*` | 신고·제재·관리 |
| `NOTIFICATION_*`, `NOTIF_*` | 알림 |
| `MARKET_*` | 마켓 데이터 |
| `NEWSBOT_*` | 뉴스봇 |

메시지 문구 전체는 서버 properties에 있으며, **프론트는 `reason`을 그대로 쓰는 것**이 원칙입니다.

---

## 8. 프론트 체크리스트

- [ ] API 클라이언트에 `Accept-Language: ko|en` 공통 적용
- [ ] 에러 UI: **`error.reason`** 표시 (code → 문구 맵 제거 또는 분기 전용으로 축소)
- [ ] 폼 검증: **`details.fieldErrors` / `violations`** 의 `message` 표시
- [ ] 로그인/권한 분기: **`error.code`** 기준 유지
- [ ] UGC 본문은 API 번역 기대하지 않음
- [ ] import·OAuth 등 **비표준 에러 본문**은 별도 스펙 확인
- [ ] 아래 breaking change 반영

---

## 9. Breaking / 마이그레이션

| 항목 | 변경 |
|------|------|
| `REPORT_ALREADY_IN_PROGRESS` | HTTP body `code`: **`MOD_420` → `MOD_429`** |
| 삭제된 code (서버에서 미사용이었음) | `AUTH_010`~`012`, `OAUTH2_001`/`002`, `USER_010`, `UPLOAD_STORAGE_*`, `COMMON_404`, `COMMON_429` 등 — 프론트 맵에만 있었다면 제거 |

---

## 10. 서버 i18n이 아닌 것 (프론트/추후)

- 화면 라벨, 네비게이션, empty state 카피 → **프론트 i18n**
- 게시글·댓글 **본문** → 작성 언어 그대로
- 게시글 **import** API의 `errors`/`warnings` 문자열 → 현재 한국어 하드코딩 위주 (추후 백엔드 작업 가능)
- OAuth 로그인 실패 등 일부 **리다이렉트/비-JSON** 플로우 → 별도

---

## 11. 로컬 테스트

1. 백엔드 기동 후 Swagger UI에서 `Accept-Language` 파라미터 지정.
2. 의도적으로 validation 실패(빈 body POST) 또는 401 호출 후 `reason` / `fieldErrors` 확인.
3. 동일 요청에 `ko` / `en` 헤더만 바꿔 `code` 동일·`reason` 변경 여부 확인.

---

## 12. 관련 소스 (백엔드)

| 파일 | 역할 |
|------|------|
| [`LocaleConfig.java`](../src/main/java/com/mocktalkback/global/config/LocaleConfig.java) | locale resolver, validation MessageSource |
| [`ApiMessageResolver.java`](../src/main/java/com/mocktalkback/global/i18n/ApiMessageResolver.java) | `reason` resolve |
| [`GlobalExceptionHandler.java`](../src/main/java/com/mocktalkback/global/exception/GlobalExceptionHandler.java) | 예외 → `ApiEnvelope` |
| [`messages_ko.properties`](../src/main/resources/messages_ko.properties) | 비즈니스 `error.*` ko |
| [`messages_en.properties`](../src/main/resources/messages_en.properties) | 비즈니스 `error.*` en |
| [`ValidationMessages_ko.properties`](../src/main/resources/ValidationMessages_ko.properties) | Bean Validation ko |
| [`ValidationMessages_en.properties`](../src/main/resources/ValidationMessages_en.properties) | Bean Validation en |

---

## 13. FAQ

**Q. 프론트 i18n과 API i18n을 같이 써도 되나요?**  
A. 네. UI는 프론트, API 에러/검증만 서버 `reason`을 쓰면 됩니다.

**Q. `reason`이 영어인데 code는 한국어 문서에만 있으면?**  
A. 분기는 항상 **`code`** 로 하세요. `reason`은 표시 전용입니다.

**Q. `Accept-Language: ko-KR` 은?**  
A. 지원 목록은 `ko`, `en` 뿐입니다. `ko-KR` 등은 fallback으로 **한국어**에 가깝게 동작할 수 있으나, **`ko` / `en`만 보내는 것**을 권장합니다.

**Q. 성공 응답 `data`도 번역되나요?**  
A. 아니요. 이번 범위는 **에러·validation** 만입니다.