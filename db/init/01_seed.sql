-- Development default admin password: ChangeMe123!
-- Change this password before any production deployment.

BEGIN;

INSERT INTO users (
    id,
    login_id,
    password_hash,
    name,
    role,
    is_active,
    created_at,
    updated_at
)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'admin',
    '$2b$12$94SYDFBPugY1crHnvG/.g.XWqGGCRytSeNa8cEPMsGHG0nloEloza',
    'Default Admin',
    'admin',
    TRUE,
    '2026-01-01T00:00:00Z',
    '2026-01-01T00:00:00Z'
)
ON CONFLICT (login_id) DO UPDATE
SET
    password_hash = EXCLUDED.password_hash,
    name = EXCLUDED.name,
    role = EXCLUDED.role,
    is_active = EXCLUDED.is_active,
    updated_at = EXCLUDED.updated_at;

INSERT INTO categories (
    id,
    user_id,
    name,
    color,
    created_at
)
VALUES
    (
        '22222222-2222-2222-2222-222222222221',
        '11111111-1111-1111-1111-111111111111',
        'Inbox',
        '#2563eb',
        '2026-01-01T00:00:00Z'
    ),
    (
        '22222222-2222-2222-2222-222222222222',
        '11111111-1111-1111-1111-111111111111',
        'Operations',
        '#059669',
        '2026-01-01T00:05:00Z'
    )
ON CONFLICT (user_id, name) DO UPDATE
SET
    color = EXCLUDED.color;

INSERT INTO todos (
    id,
    user_id,
    assignee_id,
    category_id,
    title,
    description,
    due_date,
    priority,
    completed,
    completed_at,
    estimated_minutes,
    actual_minutes,
    is_pinned,
    sort_order,
    created_at,
    updated_at
)
VALUES
    (
        '33333333-3333-3333-3333-333333333331',
        '11111111-1111-1111-1111-111111111111',
        NULL,
        '22222222-2222-2222-2222-222222222221',
        'Review seeded admin account',
        'Verify login, role, and password rotation flow for the default administrator.',
        '2026-01-02T09:00:00Z',
        'high',
        FALSE,
        NULL,
        30,
        0,
        TRUE,
        10,
        '2026-01-01T00:10:00Z',
        '2026-01-01T00:10:00Z'
    ),
    (
        '33333333-3333-3333-3333-333333333332',
        '11111111-1111-1111-1111-111111111111',
        NULL,
        '22222222-2222-2222-2222-222222222222',
        'Prepare first sprint board',
        'Create the initial backlog categories and confirm priority defaults in the UI.',
        '2026-01-03T09:00:00Z',
        'medium',
        FALSE,
        NULL,
        45,
        0,
        FALSE,
        20,
        '2026-01-01T00:15:00Z',
        '2026-01-01T00:15:00Z'
    ),
    (
        '33333333-3333-3333-3333-333333333333',
        '11111111-1111-1111-1111-111111111111',
        NULL,
        '22222222-2222-2222-2222-222222222221',
        'Archive sample completed task',
        'Reference task for completed-state rendering and dashboard counts.',
        '2025-12-31T09:00:00Z',
        'low',
        TRUE,
        '2026-01-01T08:30:00Z',
        15,
        10,
        FALSE,
        30,
        '2025-12-31T08:00:00Z',
        '2026-01-01T08:30:00Z'
    )
ON CONFLICT (id) DO UPDATE
SET
    user_id = EXCLUDED.user_id,
    assignee_id = EXCLUDED.assignee_id,
    category_id = EXCLUDED.category_id,
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    due_date = EXCLUDED.due_date,
    priority = EXCLUDED.priority,
    completed = EXCLUDED.completed,
    completed_at = EXCLUDED.completed_at,
    estimated_minutes = EXCLUDED.estimated_minutes,
    actual_minutes = EXCLUDED.actual_minutes,
    is_pinned = EXCLUDED.is_pinned,
    sort_order = EXCLUDED.sort_order,
    updated_at = EXCLUDED.updated_at;

COMMIT;
