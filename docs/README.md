# Mocktalk Backend 문서

백엔드 API 연동·운영 관련 문서 모음입니다.

| 문서 | 대상 | 설명 |
|------|------|------|
| [frontend-api-i18n.md](./frontend-api-i18n.md) | **프론트엔드** | API 다국어(`Accept-Language`), 에러/검증 응답 처리 가이드 |
| [frontend-media-view-tickets.md](./frontend-media-view-tickets.md) | **프론트엔드** | 보호 미디어 view-ticket 배치 API, 게시글 본문 연동 |

## API 스펙

- 로컬 Swagger UI: 애플리케이션 기동 후 `/swagger-ui.html` (또는 springdoc 기본 경로)
- OpenAPI에 `Accept-Language` 헤더 파라미터가 등록되어 있습니다.

## 문의

백엔드 i18n 범위·`error.code` 추가 요청은 백엔드 팀과 협의해 주세요.