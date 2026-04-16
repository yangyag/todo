# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository

https://github.com/yangyag/todo

## Project Overview

IT 회사 내부용 할일 관리 웹 서비스. 자체 계정 관리(ID/Password) 로그인, 개인별 할일 CRUD + 달력 뷰, 반응형 웹/모바일.

## Architecture

MSA 구조로 3개 서비스가 분리된다:

```
React (Vite) → API Gateway (:8080) → Auth Service (:8081)
                                    → Todo Service (:8082)
                                          ↓
                                PostgreSQL (:5432)
                                  └── todo (단일 DB, 단일 스키마)
```

- **API Gateway**: 모든 요청의 단일 진입점. JWT 서명/만료 검증 후 `X-User-Id` 헤더를 추가하여 하위 서비스로 라우팅.
- **Auth Service**: ID/Password 로그인 + BCrypt 검증 + 자체 JWT 발급 (access 15분 / refresh 7일). 계정 추가/삭제 관리자 API 포함.
- **Todo Service**: 할일 CRUD(위임/핀), 서브태스크, 카테고리/태그, 댓글, 시간 추적, 템플릿, 파일 첨부, 대시보드. `X-User-Id` 헤더로 유저 식별.
- **Frontend**: React 18 + Vite + TypeScript. Zustand로 전역 상태 관리, React Query로 서버 상태 관리.

## Directory Structure

```
todo-app/
├── front/                     # React + Vite + TypeScript
│   └── src/
│       ├── api/               # client.ts (Axios+JWT interceptor), auth.ts, todos.ts
│       ├── store/             # authStore.ts (Zustand), todoStore.ts
│       ├── pages/             # LoginPage, DashboardPage, TodoListPage, CalendarPage, TodoDetailPage
│       ├── components/
│       │   ├── todo/          # TodoCard, TodoForm, TodoList, TodoFilters, SubtaskList, CommentList, Timer, AttachmentList
│       │   ├── calendar/      # CalendarView (FullCalendar 래퍼)
│       │   └── layout/        # Navbar, Sidebar, ProtectedRoute
│       └── types/             # auth.ts, todo.ts
│
├── back/
│   ├── api-gateway/           # Spring Cloud Gateway (Reactor Netty 기반, 비동기)
│   ├── auth-service/          # Spring Security + Spring Data JPA + jjwt
│   └── todo-service/          # Spring Data JPA (Todo, Subtask, Tag, Comment, Template, TimeLog, Attachment)
│
├── nginx/nginx.conf
└── docker-compose.yml
```

## Tech Stack

| Layer | Stack |
|---|---|
| Frontend | React 18, Vite, TypeScript, Tailwind CSS v3, shadcn/ui |
| State | Zustand 4.x (전역), @tanstack/react-query v5 (서버) |
| HTTP | Axios (JWT interceptor 포함) |
| Calendar | @fullcalendar/react + daygrid + timegrid |
| Drag & Drop | @dnd-kit/core + sortable |
| Form | react-hook-form + zod |
| Backend | Spring Boot 3.x, Java 25, Gradle Kotlin DSL |
| Auth | Spring Security + BCrypt + jjwt |
| Gateway | Spring Cloud Gateway |
| ORM | Spring Data JPA + Hibernate |
| DB | PostgreSQL (단일 DB `todo`) |

## Key Design Decisions

- **인증 방식**: 자체 ID/Password 관리. BCrypt 해싱. users 테이블에 role 컬럼(`user`/`admin`)으로 권한 구분. 계정 추가/삭제는 관리자 전용 API.
- **단일 DB**: `todo` DB 하나에 11개 테이블 관리 (users, refresh_tokens, categories, todos, subtasks, tags, todo_tags, todo_comments, todo_templates, time_logs, attachments). 테이블 간 FK 정상 적용.
- **JWT 전달 방식**: access token은 Zustand 메모리에만 보관, refresh token은 localStorage. 401 응답 시 자동으로 `/api/auth/refresh` 호출 후 재시도.
- **유저 식별**: Gateway가 JWT에서 추출한 user UUID를 `X-User-Id` 헤더로 하위 서비스에 주입.
- **낙관적 업데이트**: 완료 토글/삭제/순서 변경은 즉시 UI 반영 후 실패 시 롤백.
- **파일 첨부 스토리지**: 로컬 디스크 저장 (추후 S3 전환 가능).

## Build & Run Commands

> 아직 구현 전 단계. 아래는 예정된 명령어.

```bash
# 전체 서비스 실행 (Docker Compose)
docker compose up -d

# 프론트엔드 개발 서버
cd front && npm run dev

# 백엔드 개별 서비스 실행
cd back/auth-service && ./gradlew bootRun
cd back/api-gateway && ./gradlew bootRun
cd back/todo-service && ./gradlew bootRun

# 백엔드 테스트
./gradlew test
# 단일 테스트 클래스 실행
./gradlew test --tests "com.example.ClassName"

# 프론트엔드 빌드
cd front && npm run build
```

## Prerequisites

- `application.yml`에 JWT secret key 설정 (환경변수로 관리)
- 초기 관리자 계정 seed 데이터 준비 (최초 1회 INSERT)
- 관리자 계정으로 로그인 후 일반 계정 추가/삭제 운영

## Implementation Phases

- **Phase 1**: PostgreSQL DDL (11개 테이블) → Auth Service → API Gateway → Todo Service 기본 + 확장
- **Phase 2**: React 셋업 → 로그인 → TodoList → 할일 상세(서브태스크/댓글/타이머/첨부/위임) → Calendar → Dashboard
- **Phase 3**: 반응형 레이아웃 → Dockerfile → docker-compose.yml → EC2 + Nginx + GitHub Actions CI/CD
- **Phase 4**: 칸반 보드, 타임라인(간트), 팀 대시보드, Claude AI 연동

## Progress Tracking

진행 현황은 `progress.md` 파일에서 체크리스트로 관리.
