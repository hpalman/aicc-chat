-- daclpg/acldev로 daclpg의 public에 테이블 2개 생성
CREATE TABLE chat_session (
    id BIGSERIAL PRIMARY KEY,
    room_id VARCHAR(255) NOT NULL,
    room_name VARCHAR(255),
    customer_id VARCHAR(255),
    customer_name VARCHAR(255),
    assigned_agent VARCHAR(255),
    status VARCHAR(50),
    company_id VARCHAR(255),
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    last_activity_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE chat_history (
    id BIGSERIAL PRIMARY KEY,
    room_id VARCHAR(255) NOT NULL,
    sender_id VARCHAR(255),
    sender_name VARCHAR(255),
    sender_role VARCHAR(50),
    message TEXT,
    message_type VARCHAR(50),
    company_id VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
commit;

laun
.vscode/settings.json
.vscode/launch.json