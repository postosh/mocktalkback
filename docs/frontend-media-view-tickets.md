# 프론트엔드: 보호 미디어 view-ticket 연동

웹에서 `<img>`, `<video>` 등 **브라우저 네이티브 GET**은 `Authorization` 헤더를 붙일 수 없습니다.  
보호 파일은 **ticket 쿼리**가 포함된 view URL이 필요합니다.

일반 API는 JWT, 미디어는 ticket — 이 문서는 **다수 미디어를 한 번에 ticket 발급**하는 배치 API와 **게시글 본문** 연동 방법을 설명합니다.

관련 API: [FileViewController](../src/main/java/com/mocktalkback/domain/file/controller/FileViewController.java)

---

## 1. API 요약

| 용도 | Method | Path | Auth |
|------|--------|------|------|
| **배치 발급** (본문·다수 이미지) | `POST` | `/api/files/view-tickets` | JWT 필수 |
| 단건 발급 (에디터·재시도) | `POST` | `/api/files/{fileId}/view-ticket?variant=` | JWT 필수 |
| 실제 미디어 로드 | `GET` | `/api/files/{fileId}/view?ticket=&variant=` | 없음 (ticket으로 검증) |

- 보호 파일: 응답 `viewUrl`에 `ticket=fv_...` 포함, `expiresInSec` > 0
- 공개 파일: ticket 없음, `expiresInSec` = 0
- `GET /view`는 **302**로 object storage presigned URL로 리다이렉트 (기존과 동일)

---

## 2. 배치 요청 / 응답

### Request

```http
POST /api/files/view-tickets
Authorization: Bearer <access_token>
Content-Type: application/json
Accept-Language: ko
```

```json
{
  "items": [
    { "fileId": 31, "variant": "medium" },
    { "fileId": 44 }
  ]
}
```

- `items`: 1~100개 (`@Size(max=100)`). 서버 설정 `app.object-storage.view-ticket-batch-max-items`와 맞춤.
- `variant`: 생략 가능. 본문 HTML에 이미 `?variant=thumb` 등이 있으면 **그대로** 넘깁니다.

### Response (`ApiEnvelope.data`)

```json
{
  "items": [
    {
      "fileId": 31,
      "variant": "medium",
      "success": true,
      "viewUrl": "/api/files/31/view?variant=medium&ticket=fv_...",
      "expiresInSec": 120,
      "protectedFile": true,
      "errorCode": null
    },
    {
      "fileId": 99,
      "variant": null,
      "success": false,
      "viewUrl": null,
      "expiresInSec": 0,
      "protectedFile": false,
      "errorCode": "FILE_404"
    }
  ]
}
```

- HTTP **200** — 일부만 실패해도 전체 요청은 성공.
- `success: false` — 없는 파일·권한 없음 등 (보안상 모두 `FILE_404`로 통일).
- 요청 validation 실패(빈 배열, 101개 초과): **400** + `COMMON_400` 등.

동일 `(fileId, variant)`가 여러 번 오면 서버가 **ticket을 한 번만** 발급하고 같은 `viewUrl`을 재사용합니다.

---

## 3. 게시글 본문 연동 (권장 플로우)

`ArticleDetailResponse.content`는 HTML이며, 이미지·영상 src에 `/api/files/{id}/view` 형태가 들어갑니다.

### 3.1 URL 파싱

백엔드 import와 동일한 패턴:

```text
/api/files/(\d+)/view
```

예:

- `/api/files/31/view`
- `/api/files/31/view?variant=medium`

`variant`는 URL query에서 추출해 배치 `items[].variant`에 넣습니다.

`img[src]`, `video[src]`, `source[src]`를 모두 스캔하세요.

### 3.2 렌더 순서 (깜빡임 + 스피너)

1. 상세 API로 `content` 수신
2. 본문 영역에 **로딩 스피너** 표시 (고정 `min-height` 권장)
3. HTML에서 fileId(+variant) 수집 → **dedupe**
4. `POST /api/files/view-tickets` (100개 초과 시 **청크**로 나눠 순차/병렬 호출)
5. 성공 항목만 `src`를 `viewUrl`로 **일괄 교체** 후 HTML 삽입/표시
6. 스피너 제거

ticket 적용 전에는 placeholder/spinner만 보이므로, 빈 `src` → 깜빡 → 로드 패턴을 줄일 수 있습니다.

### 3.3 청크 예시

```typescript
const BATCH_MAX = 100;

async function issueViewTickets(items: ViewTicketItem[]) {
  const results = new Map<string, BatchItemResult>();
  for (let i = 0; i < items.length; i += BATCH_MAX) {
    const chunk = items.slice(i, i + BATCH_MAX);
    const { data } = await api.post('/api/files/view-tickets', { items: chunk });
    for (const row of data.items) {
      results.set(cacheKey(row.fileId, row.variant), row);
    }
  }
  return results;
}
```

### 3.4 만료·에러

- `expiresInSec` 경과 전: 동일 `viewUrl` 재사용 가능 (Redis ticket TTL ≈ presign 상한).
- `img onerror` / 404: 해당 fileId만 **단건** `POST .../view-ticket` 또는 소량 배치로 재발급.
- `success: false`: 해당 슬롯만 broken image / placeholder, **본문 나머지는 정상 표시**.

---

## 4. 공개 vs 보호

| | `protectedFile` | ticket | 비고 |
|--|-----------------|--------|------|
| 공개 | `false` | 없음 | `viewUrl`만으로 `<img src>` 가능 |
| 보호 | `true` | 필요 | 게시글 본문·비공개 게시판 이미지 등 |

배치 응답의 `viewUrl`을 **항상** 최종 `src`로 사용하면 공개/보호를 프론트에서 분기할 필요가 줄어듭니다.

---

## 5. 단건 API (유지)

```http
POST /api/files/31/view-ticket?variant=medium
```

에디터 미리보기, 단일 썸네일, 배치 실패 항목 재시도에 사용합니다.

---

## 6. 쿠키 / 모바일 앱

- **이번 설계**: query **ticket 유지**, 쿠키 전환 없음.
- **추후 네이티브 앱**: HTTP 클라이언트에서 `Authorization` + `GET /view` 조합 검토 가능 (웹과 인증 수단만 다름).

---

## 7. 체크리스트

- [ ] API 클라이언트 JWT + `Accept-Language` ([frontend-api-i18n.md](./frontend-api-i18n.md))
- [ ] 본문: parse → 배치 ticket → src 일괄 설정
- [ ] 로딩 스피너 / min-height
- [ ] 100개 초과 청크
- [ ] `onError` 시 단건 재발급
- [ ] 실패 항목 placeholder 처리

---

## 8. Swagger

애플리케이션 기동 후 `/swagger-ui.html` → **FileView** 태그에서 배치·단건 API 스키마 확인.