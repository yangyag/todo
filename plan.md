# Todo List Web App 구현 플랜

## Overview
IT 회사 내부용 할일 관리 웹 서비스.
- 로그인: 자체 계정 관리 (ID/Password 기반)
- 계정 추가/삭제는 관리자가 직접 수행
- 개인별 할일 CRUD + 달력 뷰
- 웹/스마트폰 반응형
- 백엔드: Spring Boot MSA (Gateway + Auth + Todo)
- 프론트: React 단독 분리
- Claude AI 연동: 추후 개선

---

## 전체 아키텍처

```
[ React (Vite) + Tailwind ]
         ↓  HTTPS
[ API Gateway (Spring Cloud Gateway) :8080 ]
         ↓  JWT 검증 후 라우팅
    ↙               ↘
[Auth Service      [Todo Service
  :8081]              :8082]
    ↓                   ↓
[ PostgreSQL (기존 Docker :5432) ]
  └── todo (단일 DB, 단일 스키마)
```

---

## 기술 스택

| 영역 | 기술 |
|---|---|
| Frontend | React 18 + Vite + TypeScript |
| UI / 반응형 | Tailwind CSS v3 + shadcn/ui (Radix UI 기반) |
| API Gateway | Spring Cloud Gateway (Spring Boot 3.x) |
| Auth Service | Spring Boot 3.x + Spring Security + Spring Data JPA |
| Todo Service | Spring Boot 3.x + Spring Data JPA |
| DB | PostgreSQL (기존 Docker 재사용) |
| Auth 방식 | ID/Password 로그인 → 자체 JWT 발급 |
| 배포 | AWS EC2 + Docker Compose + Nginx |
| 빌드 | Gradle (Kotlin DSL) |
| Java 버전 | Java 25 |

---

## 서비스 역할 분담

### 1. API Gateway (:8080)
- 모든 클라이언트 요청의 단일 진입점
- JWT 유효성 검증 필터 (자체 검증)
- 라우팅 규칙:
  - `/api/auth/**` → Auth Service
  - `/api/todos/**` → Todo Service
  - `/api/categories/**` → Todo Service
  - `/api/tags/**` → Todo Service
  - `/api/templates/**` → Todo Service
  - `/api/dashboard/**` → Todo Service
- CORS 설정 (프론트 도메인 허용)

### 2. Auth Service (:8081)
- ID/Password 로그인 처리 (BCrypt 해싱)
- 자체 JWT 발급 (access 15분 / refresh 7일)
- 계정 추가/삭제 (관리자 전용 API)
- refresh token 갱신 / 로그아웃(무효화)
- DB: `todo` (공용 DB)

### 3. Todo Service (:8082)
- 할일 CRUD (위임, 핀 고정 포함)
- 서브태스크 관리
- 카테고리 / 태그 관리
- 댓글
- 시간 추적 (타이머)
- 템플릿
- 파일 첨부
- 대시보드 통계
- Gateway에서 전달받은 `X-User-Id` 헤더로 유저 식별
- DB: `todo` (공용 DB)

---

## 인증 흐름

```
1. 유저가 ID/Password 입력 후 로그인 버튼 클릭
2. 프론트 → POST /api/auth/login (body: { loginId, password })
3. Auth Service:
   - loginId로 users 테이블 조회
   - BCrypt로 password 검증
   - 자체 JWT (access + refresh) 발급
4. 응답: { accessToken, refreshToken }
5. 이후 모든 API 요청: Authorization: Bearer {accessToken}
```

---

## DB 스키마

> 단일 PostgreSQL DB (`todo`)에 모든 테이블 관리. 스키마 분리 없음.

#### users
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id | UUID PK | gen_random_uuid() |
| login_id | VARCHAR(100) | UNIQUE NOT NULL (로그인용 ID) |
| password_hash | VARCHAR(255) | NOT NULL (BCrypt) |
| name | VARCHAR(100) | NOT NULL |
| role | VARCHAR(20) | 'user'/'admin', DEFAULT 'user' |
| is_active | BOOLEAN | DEFAULT TRUE |
| created_at | TIMESTAMPTZ | DEFAULT now() |
| updated_at | TIMESTAMPTZ | |

#### refresh_tokens
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id | UUID PK | |
| user_id | UUID FK | → users.id, ON DELETE CASCADE |
| token_hash | VARCHAR(255) | UNIQUE |
| expires_at | TIMESTAMPTZ | |
| created_at | TIMESTAMPTZ | |

#### categories
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id | UUID PK | |
| user_id | UUID FK | → users.id, ON DELETE CASCADE |
| name | VARCHAR(100) | NOT NULL |
| color | VARCHAR(7) | hex, DEFAULT '#6366f1' |
| created_at | TIMESTAMPTZ | |

UNIQUE: (user_id, name)

#### todos
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id | UUID PK | |
| user_id | UUID FK | → users.id, ON DELETE CASCADE |
| assignee_id | UUID FK | → users.id, NULLABLE (위임받은 사람) |
| category_id | UUID FK | → categories.id, ON DELETE SET NULL |
| title | VARCHAR(500) | NOT NULL |
| description | TEXT | NULLABLE |
| due_date | TIMESTAMPTZ | NULLABLE |
| priority | VARCHAR(10) | 'low'/'medium'/'high', DEFAULT 'medium' |
| completed | BOOLEAN | DEFAULT FALSE |
| completed_at | TIMESTAMPTZ | NULLABLE |
| estimated_minutes | INTEGER | NULLABLE (예상 소요 시간) |
| actual_minutes | INTEGER | NULLABLE (실제 소요 시간, 타이머 누적) |
| is_pinned | BOOLEAN | DEFAULT FALSE (즐겨찾기/핀) |
| sort_order | INTEGER | 드래그 정렬용, DEFAULT 0 |
| created_at | TIMESTAMPTZ | |
| updated_at | TIMESTAMPTZ | |

인덱스: (user_id), (assignee_id), (user_id, due_date), (user_id, completed)

#### subtasks
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id | UUID PK | |
| todo_id | UUID FK | → todos.id, ON DELETE CASCADE |
| title | VARCHAR(500) | NOT NULL |
| completed | BOOLEAN | DEFAULT FALSE |
| sort_order | INTEGER | DEFAULT 0 |
| created_at | TIMESTAMPTZ | |

#### tags
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id | UUID PK | |
| user_id | UUID FK | → users.id, ON DELETE CASCADE |
| name | VARCHAR(50) | NOT NULL |
| created_at | TIMESTAMPTZ | |

UNIQUE: (user_id, name)

#### todo_tags
| 컬럼 | 타입 | 비고 |
|---|---|---|
| todo_id | UUID FK | → todos.id, ON DELETE CASCADE |
| tag_id | UUID FK | → tags.id, ON DELETE CASCADE |

PK: (todo_id, tag_id)

#### todo_comments
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id | UUID PK | |
| todo_id | UUID FK | → todos.id, ON DELETE CASCADE |
| user_id | UUID FK | → users.id, ON DELETE CASCADE |
| content | TEXT | NOT NULL |
| created_at | TIMESTAMPTZ | |
| updated_at | TIMESTAMPTZ | |

#### todo_templates
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id | UUID PK | |
| user_id | UUID FK | → users.id, ON DELETE CASCADE |
| name | VARCHAR(100) | NOT NULL |
| template_data | JSONB | 할일 필드 + 서브태스크 목록 저장 |
| created_at | TIMESTAMPTZ | |

#### time_logs
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id | UUID PK | |
| todo_id | UUID FK | → todos.id, ON DELETE CASCADE |
| user_id | UUID FK | → users.id, ON DELETE CASCADE |
| started_at | TIMESTAMPTZ | NOT NULL |
| ended_at | TIMESTAMPTZ | NULLABLE (NULL = 타이머 진행 중) |
| minutes | INTEGER | NULLABLE (ended_at 시 계산) |
| created_at | TIMESTAMPTZ | |

#### attachments
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id | UUID PK | |
| todo_id | UUID FK | → todos.id, ON DELETE CASCADE |
| user_id | UUID FK | → users.id, ON DELETE CASCADE |
| file_name | VARCHAR(255) | NOT NULL (원본 파일명) |
| file_path | VARCHAR(500) | NOT NULL (서버 저장 경로) |
| file_size | BIGINT | NOT NULL (bytes) |
| content_type | VARCHAR(100) | NOT NULL (MIME 타입) |
| created_at | TIMESTAMPTZ | |

---

## API 엔드포인트

### Auth Service (/api/auth)
| Method | Path | 설명 |
|---|---|---|
| POST | `/api/auth/login` | ID/Password 로그인 → JWT 발급 |
| POST | `/api/auth/refresh` | access_token 갱신 |
| POST | `/api/auth/logout` | refresh_token 무효화 |
| GET | `/api/auth/me` | 현재 유저 정보 |
| POST | `/api/auth/users` | 계정 추가 (관리자 전용) |
| DELETE | `/api/auth/users/{id}` | 계정 삭제 (관리자 전용) |
| GET | `/api/auth/users` | 계정 목록 조회 (관리자 전용) |

### Todo Service
Gateway에서 `X-User-Id: {uuid}` 헤더를 주입해서 전달.

**Todos (/api/todos)**
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/todos` | 목록 (필터: completed, priority, category_id, tag_id, assignee_id, due_before, due_after, search, sort_by, pinned) |
| POST | `/api/todos` | 생성 |
| GET | `/api/todos/{id}` | 단건 조회 |
| PATCH | `/api/todos/{id}` | 부분 수정 |
| DELETE | `/api/todos/{id}` | 삭제 |
| PATCH | `/api/todos/{id}/complete` | 완료 토글 |
| PATCH | `/api/todos/{id}/pin` | 핀 토글 |
| PATCH | `/api/todos/{id}/assign` | 다른 유저에게 위임 |
| PATCH | `/api/todos/reorder` | 드래그 순서 일괄 저장 |
| POST | `/api/todos/from-template/{templateId}` | 템플릿으로 할일 생성 |

**Subtasks (/api/todos/{todoId}/subtasks)**
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/todos/{todoId}/subtasks` | 서브태스크 목록 |
| POST | `/api/todos/{todoId}/subtasks` | 서브태스크 추가 |
| PATCH | `/api/todos/{todoId}/subtasks/{id}` | 수정 (완료 토글 포함) |
| DELETE | `/api/todos/{todoId}/subtasks/{id}` | 삭제 |

**Comments (/api/todos/{todoId}/comments)**
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/todos/{todoId}/comments` | 댓글 목록 |
| POST | `/api/todos/{todoId}/comments` | 댓글 작성 |
| PATCH | `/api/todos/{todoId}/comments/{id}` | 댓글 수정 |
| DELETE | `/api/todos/{todoId}/comments/{id}` | 댓글 삭제 |

**Tags (/api/tags)**
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/tags` | 태그 목록 |
| POST | `/api/tags` | 태그 생성 |
| DELETE | `/api/tags/{id}` | 태그 삭제 |

**Time Tracking (/api/todos/{todoId}/timer)**
| Method | Path | 설명 |
|---|---|---|
| POST | `/api/todos/{todoId}/timer/start` | 타이머 시작 |
| POST | `/api/todos/{todoId}/timer/stop` | 타이머 정지 |
| GET | `/api/todos/{todoId}/timer/logs` | 시간 기록 목록 |

**Templates (/api/templates)**
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/templates` | 템플릿 목록 |
| POST | `/api/templates` | 템플릿 저장 |
| DELETE | `/api/templates/{id}` | 템플릿 삭제 |

**Attachments (/api/todos/{todoId}/attachments)**
| Method | Path | 설명 |
|---|---|---|
| POST | `/api/todos/{todoId}/attachments` | 파일 업로드 |
| GET | `/api/todos/{todoId}/attachments` | 첨부 파일 목록 |
| GET | `/api/todos/{todoId}/attachments/{id}/download` | 파일 다운로드 |
| DELETE | `/api/todos/{todoId}/attachments/{id}` | 파일 삭제 |

**Categories (/api/categories)**
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/categories` | 목록 |
| POST | `/api/categories` | 생성 |
| PATCH | `/api/categories/{id}` | 수정 |
| DELETE | `/api/categories/{id}` | 삭제 |

**Dashboard (/api/dashboard)**
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/dashboard/stats` | 전체/완료/기한초과 카운트 |
| GET | `/api/dashboard/upcoming` | 7일 내 마감 할일 |

---

## 현재 저장소 상태

> 2026-04-17 기준으로 실제 저장소에는 아래 구조가 확인된다. `front/`, `back/todo-service/`, `docker-compose.yml`은 아직 없다. 목표 구조는 다음 섹션에 별도로 정리한다.

```
todo-app/
├── back/
│   ├── api-gateway/
│   │   ├── src/main/resources/application.yml
│   │   └── src/main/java/.../filter/JwtAuthFilter.java
│   └── auth-service/
│       ├── src/main/resources/application.yml
│       ├── src/main/java/.../controller/AuthController.java
│       └── src/main/java/.../controller/AdminUserController.java
├── db/
│   └── init/
│       ├── 00_schema.sql
│       └── 01_seed.sql
└── nginx/
```

---

## 목표 프로젝트 디렉토리 구조

> 목표 최상위 구조는 `front/`와 `back/`를 유지한다. 현재는 이 목표 중 `back/auth-service`, `back/api-gateway`, `db/init` 일부만 구현되어 있고 나머지는 단계적으로 채운다.

```
todo-app/
├── front/                                 # React + Vite
│   └── src/
│       ├── api/                           # client.ts (Axios+interceptor), auth.ts, todos.ts
│       ├── store/                         # authStore.ts (Zustand), todoStore.ts
│       ├── pages/                         # LoginPage, DashboardPage, TodoListPage, CalendarPage, TodoDetailPage
│       ├── components/
│       │   ├── todo/                      # TodoCard, TodoForm, TodoList, TodoFilters, SubtaskList, CommentList, Timer, AttachmentList
│       │   ├── calendar/                  # CalendarView (FullCalendar 래퍼)
│       │   └── layout/                    # Navbar, Sidebar, ProtectedRoute
│       └── types/                         # auth.ts, todo.ts
│
├── back/
│   ├── api-gateway/                       # Spring Cloud Gateway
│   │   └── src/main/
│   │       ├── resources/
│   │       │   └── application.yml        # 라우팅 규칙, CORS, JWT 설정
│   │       └── java/.../
│   │           └── filter/                # JwtAuthFilter.java
│   │
│   ├── auth-service/                      # Spring Boot + JWT (자체 계정 관리)
│   │   └── src/main/java/.../
│   │       ├── controller/                # AuthController.java, AdminUserController.java
│   │       ├── service/                   # AuthService.java, UserManagementService.java
│   │       ├── security/                  # JwtService.java, JwtAuthenticationFilter.java
│   │       ├── entity/                    # User.java, RefreshToken.java
│   │       ├── repository/                # UserRepository.java, RefreshTokenRepository.java
│   │       └── config/                    # SecurityConfig.java
│   │
│   └── todo-service/                      # Spring Boot JPA
│       └── src/main/java/.../
│           ├── controller/                # 할일/카테고리/대시보드/부가 기능 API
│           ├── service/                   # 도메인 서비스 계층
│           ├── entity/                    # Todo, Category, Subtask, Tag, TodoComment 등
│           ├── repository/                # 도메인별 Repository
│           └── dto/                       # 기능별 요청/응답 DTO
│
├── nginx/
│   └── nginx.conf
└── docker-compose.yml
```

---

## 주요 라이브러리

### Backend 공통
- Spring Boot 3.x, Java 25, Gradle Kotlin DSL
- Spring Data JPA + Hibernate
- PostgreSQL Driver
- Lombok, MapStruct

### Auth Service 추가
- Spring Security (폼 로그인 기반)
- jjwt (JWT 발급/검증)
- Spring Security Crypto (BCrypt 패스워드 해싱)

### API Gateway 추가
- Spring Cloud Gateway
- Reactor Netty (비동기)

### Frontend
| 목적 | 라이브러리 |
|---|---|
| 서버 상태 관리 | @tanstack/react-query v5 |
| 전역 상태 | zustand 4.x + persist |
| HTTP 클라이언트 | axios (JWT interceptor) |
| 달력 | @fullcalendar/react + daygrid + timegrid |
| 드래그 정렬 | @dnd-kit/core + sortable |
| UI 컴포넌트 | tailwindcss v3 + shadcn/ui |
| 폼 검증 | react-hook-form + zod |
| 날짜 처리 | date-fns |
| 아이콘 | lucide-react |
| 토스트 | sonner |

---

## 주요 화면 (반응형)

| 화면 | 설명 |
|---|---|
| 로그인 | ID/Password 입력 폼. 모바일/데스크톱 모두 심플 |
| 대시보드 | 통계 카드 (전체/완료/기한초과/이번주) + 오늘 마감 할일 |
| 할일 목록 | 좌: 카테고리/태그 사이드바 (모바일은 하단 탭) + 필터+검색 + 드래그 정렬 카드 + 서브태스크 진행률 |
| 할일 상세 | 모달 또는 페이지. 서브태스크 체크리스트, 댓글 타임라인, 타이머, 파일 첨부, 위임 설정 |
| 달력 | FullCalendar 월/주 뷰. 드래그로 due_date 변경 가능 |

### UX 포인트
- **낙관적 업데이트** — 완료/삭제/순서변경 즉시 반영, 실패 시 롤백
- **반응형** — 모바일: 하단 탭 네비 / 데스크톱: 사이드바
- **드래그 정렬** — dnd-kit 리스트 / FullCalendar 날짜 드래그
- **빠른 필터** — "오늘 마감", "기한초과", "이번 주", "내게 위임된", "핀 고정" 원클릭 칩
- **서브태스크 진행률** — 카드에 (완료/전체) 프로그레스바 표시
- **타이머 UI** — 할일 상세에서 시작/정지 버튼, 경과 시간 실시간 표시
- **댓글** — 할일 상세 하단에 타임라인 형태로 표시
- **파일 첨부** — 할일 상세에서 드래그 앤 드롭 또는 파일 선택으로 업로드
- **다크모드** — 시스템 설정 감지 + 수동 토글

### 추후 개선 (Phase 4)
- 칸반 보드 뷰 (To Do / In Progress / Done)
- 타임라인(간트) 뷰
- 팀 대시보드 (팀원별 완료율, 주간 생산성 그래프)

---

## 인증 흐름 요약

```
ID/Password 로그인 완료
  → Auth Service가 자체 JWT 발급
  → access_token: 메모리 (Zustand)
  → refresh_token: localStorage

모든 API 요청
  → Authorization: Bearer {access_token}
  → Gateway가 JWT 서명/만료 검증
  → 유효하면 X-User-Id 헤더 추가 후 서비스로 전달

401 응답 (만료)
  → /api/auth/refresh 호출 → 새 access_token 발급 → 재시도
```

---

## EC2 배포 구조

```
인터넷 → Nginx (:80/:443)
              ├── /api/**  →  API Gateway (:8080)
              └── /**      →  React 빌드 파일 (정적 서빙)

API Gateway (:8080)
  ├── auth-service (:8081)
  └── todo-service (:8082)
         ↓
  PostgreSQL (기존 Docker :5432)
    └── todo (단일 DB)
```

- `docker-compose.yml`로 전체 서비스 통합 관리
- 기존 PostgreSQL Docker 컨테이너는 외부 네트워크로 연결
- GitHub Actions: main push → EC2 SSH → `docker compose up -d`

---

## ⚠️ 구현 전 사전 준비

1. `application.yml`에 JWT secret key 설정 (환경변수로 관리)
2. 초기 관리자 계정 seed 데이터 준비 (최초 1회 INSERT)
3. 관리자 계정으로 로그인 후 일반 계정 추가/삭제 운영

---

## 구현 순서

### Phase 1 — 백엔드 MSA 기반
1. PostgreSQL todo DB 전체 테이블 생성 (DDL 스크립트: users, refresh_tokens, categories, todos, subtasks, tags, todo_tags, todo_comments, todo_templates, time_logs, attachments)
2. Auth Service: ID/Password 로그인 + BCrypt 검증 + JWT 발급 + 계정 관리 API
3. API Gateway: 라우팅 + JWT 검증 필터
4. Todo Service 기본: 할일 CRUD + 카테고리 + 대시보드 API
5. Todo Service 확장: 서브태스크, 태그, 댓글, 타이머, 템플릿, 위임, 파일 첨부

### Phase 2 — 프론트엔드
6. React + Vite 셋업 (Tailwind + shadcn)
7. 로그인 페이지 (ID/Password 폼 → JWT 저장)
8. TodoList 페이지 (필터 + 드래그 + 핀)
9. 할일 상세 뷰 (서브태스크 + 댓글 + 타이머 + 파일 첨부 + 위임)
10. Calendar 페이지 (FullCalendar)
11. Dashboard 페이지

### Phase 3 — 반응형 + 배포
12. 모바일 반응형 레이아웃 (하단 탭, 터치 제스처)
13. Dockerfile 작성 (각 서비스별)
14. docker-compose.yml 구성
15. EC2 배포 + Nginx SSL + GitHub Actions CI/CD

---

## 검증 방법
- 백엔드: 각 서비스 단위 테스트 + Gateway 통합 테스트
- 프론트: 브라우저 + 크롬 DevTools 모바일 뷰 반응형 확인
- E2E: ID/Password 로그인 → 할일 CRUD → 서브태스크 → 댓글 → 태그 → 타이머 → 파일 첨부 → 위임 → 달력 → 드래그 정렬 → 로그아웃 전체 흐름
