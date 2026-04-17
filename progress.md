# Todo App 구현 진행 현황

> plan.md 기반 체크리스트. 각 단계 완료 시 `[ ]` → `[x]`로 변경.

---

## Phase 1 — 백엔드 MSA 기반

### 1-1. PostgreSQL DDL
- [x] users 테이블
- [x] refresh_tokens 테이블
- [x] categories 테이블
- [x] todos 테이블
- [x] subtasks 테이블
- [x] tags 테이블
- [x] todo_tags 테이블
- [x] todo_comments 테이블
- [x] todo_templates 테이블
- [x] time_logs 테이블
- [x] attachments 테이블
- [x] 초기 관리자 계정 seed 데이터

### 1-2. Auth Service
- [x] Gradle 프로젝트 셋업 (Spring Boot 3.x, Java 25)
- [x] Entity: User, RefreshToken
- [x] Repository: UserRepository, RefreshTokenRepository
- [x] POST /api/auth/login (ID/Password → JWT 발급)
- [x] POST /api/auth/refresh (토큰 갱신)
- [x] POST /api/auth/logout (refresh token 무효화)
- [x] GET /api/auth/me (현재 유저 정보)
- [x] POST /api/auth/users (계정 추가 — 관리자 전용)
- [x] DELETE /api/auth/users/{id} (계정 삭제 — 관리자 전용)
- [x] GET /api/auth/users (계정 목록 — 관리자 전용)
- [x] JwtService (발급/검증)
- [x] SecurityConfig (BCrypt, 필터 체인)

### 1-3. API Gateway
- [x] Gradle 프로젝트 셋업
- [x] JwtAuthFilter (JWT 검증 → X-User-Id 헤더 주입)
- [x] 라우팅 규칙 (auth, todos, categories, tags, templates, dashboard)
- [x] CORS 설정

### 1-4. Todo Service 기본
- [ ] Gradle 프로젝트 셋업
- [ ] Entity: Todo, Category
- [ ] Repository: TodoRepository, CategoryRepository
- [ ] 할일 CRUD (GET/POST/PATCH/DELETE /api/todos)
- [ ] 완료 토글 (PATCH /api/todos/{id}/complete)
- [ ] 핀 토글 (PATCH /api/todos/{id}/pin)
- [ ] 위임 (PATCH /api/todos/{id}/assign)
- [ ] 드래그 순서 저장 (PATCH /api/todos/reorder)
- [ ] 카테고리 CRUD (GET/POST/PATCH/DELETE /api/categories)
- [ ] 대시보드 통계 (GET /api/dashboard/stats)
- [ ] 대시보드 upcoming (GET /api/dashboard/upcoming)

### 1-5. Todo Service 확장
- [ ] Entity: Subtask, Tag, TodoComment, TodoTemplate, TimeLog, Attachment
- [ ] 서브태스크 CRUD (/api/todos/{todoId}/subtasks)
- [ ] 태그 CRUD (/api/tags + todo-tag 연결)
- [ ] 댓글 CRUD (/api/todos/{todoId}/comments)
- [ ] 타이머 시작/정지 (/api/todos/{todoId}/timer)
- [ ] 템플릿 CRUD (/api/templates)
- [ ] 템플릿으로 할일 생성 (POST /api/todos/from-template/{templateId})
- [ ] 파일 첨부 업로드/목록/다운로드/삭제 (/api/todos/{todoId}/attachments)

---

## Phase 2 — 프론트엔드

### 2-1. 프로젝트 셋업
- [ ] Vite + React 18 + TypeScript 초기화
- [ ] Tailwind CSS v3 설정
- [ ] shadcn/ui 설치 및 설정
- [ ] Axios 클라이언트 (JWT interceptor, 401 자동 갱신)
- [ ] Zustand 스토어 (authStore, todoStore)
- [ ] React Query 설정
- [ ] 라우터 설정 (ProtectedRoute 포함)

### 2-2. 로그인 페이지
- [ ] ID/Password 입력 폼
- [ ] 로그인 API 연동 → JWT 저장
- [ ] 로그인 실패 에러 처리

### 2-3. 할일 목록 페이지
- [ ] TodoList 컴포넌트 (카드 형태)
- [ ] 카테고리/태그 사이드바
- [ ] 필터 (completed, priority, category, tag, assignee, due_date)
- [ ] 검색
- [ ] 빠른 필터 칩 (오늘 마감, 기한초과, 이번 주, 내게 위임된, 핀 고정)
- [ ] 드래그 정렬 (dnd-kit)
- [ ] 서브태스크 진행률 표시
- [ ] 할일 생성 폼

### 2-4. 할일 상세 뷰
- [ ] 할일 기본 정보 표시/수정
- [ ] 서브태스크 체크리스트
- [ ] 댓글 타임라인
- [ ] 타이머 (시작/정지, 경과 시간 실시간 표시)
- [ ] 파일 첨부 (드래그 앤 드롭 + 파일 선택)
- [ ] 위임 설정 (유저 선택)
- [ ] 태그 추가/제거

### 2-5. 달력 페이지
- [ ] FullCalendar 월/주 뷰
- [ ] 할일 이벤트 표시
- [ ] 드래그로 due_date 변경

### 2-6. 대시보드 페이지
- [ ] 통계 카드 (전체/완료/기한초과/이번주)
- [ ] 오늘 마감 할일 목록
- [ ] 7일 내 마감 할일 목록

---

## Phase 3 — 반응형 + 배포

### 3-1. 반응형 레이아웃
- [ ] 모바일 하단 탭 네비게이션
- [ ] 데스크톱 사이드바
- [ ] 터치 제스처 대응
- [ ] 다크모드 (시스템 감지 + 수동 토글)

### 3-2. Docker + 배포
- [ ] Dockerfile: front
- [ ] Dockerfile: api-gateway
- [ ] Dockerfile: auth-service
- [ ] Dockerfile: todo-service
- [ ] docker-compose.yml 구성
- [ ] Nginx 설정 (SSL, 정적 파일 서빙, API 프록시)
- [ ] EC2 배포
- [ ] GitHub Actions CI/CD (main push → docker compose up)

---

## Phase 4 — 추후 개선
- [ ] 칸반 보드 뷰
- [ ] 타임라인(간트) 뷰
- [ ] 팀 대시보드
- [ ] Claude AI 연동
