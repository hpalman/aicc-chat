-- =====================================================
-- AICC Chat System - PostgreSQL Database Schema
-- =====================================================
-- 데이터베이스 생성
-- CREATE DATABASE aicc_chat;

-- 데이터베이스 연결
-- \c aicc_chat;

-- =====================================================
-- 1. 채팅 세션 테이블
-- =====================================================
CREATE TABLE IF NOT EXISTS chat_session (
    id BIGSERIAL PRIMARY KEY,
    room_id VARCHAR(100) NOT NULL UNIQUE,
    room_name VARCHAR(255),
    customer_id VARCHAR(100) NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    assigned_agent VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'BOT',
    company_id VARCHAR(100),
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMP,
    last_activity_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 인덱스 생성
CREATE INDEX idx_chat_session_room_id ON chat_session(room_id);
CREATE INDEX idx_chat_session_customer_id ON chat_session(customer_id);
CREATE INDEX idx_chat_session_assigned_agent ON chat_session(assigned_agent);
CREATE INDEX idx_chat_session_status ON chat_session(status);
CREATE INDEX idx_chat_session_company_id ON chat_session(company_id);
CREATE INDEX idx_chat_session_started_at ON chat_session(started_at);
CREATE INDEX idx_chat_session_last_activity_at ON chat_session(last_activity_at);

-- 테이블 코멘트
COMMENT ON TABLE chat_session IS '채팅 상담 세션 정보';
COMMENT ON COLUMN chat_session.id IS '세션 고유 ID (자동 증가)';
COMMENT ON COLUMN chat_session.room_id IS '채팅방 ID (고유값)';
COMMENT ON COLUMN chat_session.room_name IS '채팅방 이름';
COMMENT ON COLUMN chat_session.customer_id IS '고객 ID';
COMMENT ON COLUMN chat_session.customer_name IS '고객 이름';
COMMENT ON COLUMN chat_session.assigned_agent IS '배정된 상담원 이름';
COMMENT ON COLUMN chat_session.status IS '세션 상태 (BOT, WAITING, AGENT, CLOSED)';
COMMENT ON COLUMN chat_session.company_id IS '회사 ID';
COMMENT ON COLUMN chat_session.started_at IS '상담 시작 시간';
COMMENT ON COLUMN chat_session.ended_at IS '상담 종료 시간';
COMMENT ON COLUMN chat_session.last_activity_at IS '마지막 활동 시간';
COMMENT ON COLUMN chat_session.created_at IS '생성 시간';
COMMENT ON COLUMN chat_session.updated_at IS '수정 시간';

-- =====================================================
-- 2. 채팅 이력 테이블
-- =====================================================
CREATE TABLE IF NOT EXISTS chat_history (
    id BIGSERIAL PRIMARY KEY,
    room_id VARCHAR(100) NOT NULL,
    sender_id VARCHAR(100) NOT NULL,
    sender_name VARCHAR(255) NOT NULL,
    sender_role VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    message_type VARCHAR(50) NOT NULL,
    company_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 인덱스 생성
CREATE INDEX idx_chat_history_room_id ON chat_history(room_id);
CREATE INDEX idx_chat_history_sender_id ON chat_history(sender_id);
CREATE INDEX idx_chat_history_sender_role ON chat_history(sender_role);
CREATE INDEX idx_chat_history_message_type ON chat_history(message_type);
CREATE INDEX idx_chat_history_company_id ON chat_history(company_id);
CREATE INDEX idx_chat_history_created_at ON chat_history(created_at);
CREATE INDEX idx_chat_history_room_created ON chat_history(room_id, created_at);

-- 테이블 코멘트
COMMENT ON TABLE chat_history IS '채팅 메시지 이력';
COMMENT ON COLUMN chat_history.id IS '이력 고유 ID (자동 증가)';
COMMENT ON COLUMN chat_history.room_id IS '채팅방 ID';
COMMENT ON COLUMN chat_history.sender_id IS '발신자 ID';
COMMENT ON COLUMN chat_history.sender_name IS '발신자 이름';
COMMENT ON COLUMN chat_history.sender_role IS '발신자 역할 (CUSTOMER, AGENT, BOT, SYSTEM)';
COMMENT ON COLUMN chat_history.message IS '메시지 내용';
COMMENT ON COLUMN chat_history.message_type IS '메시지 타입 (ENTER, TALK, LEAVE, JOIN, HANDOFF, CANCEL_HANDOFF)';
COMMENT ON COLUMN chat_history.company_id IS '회사 ID';
COMMENT ON COLUMN chat_history.created_at IS '생성 시간';
COMMENT ON COLUMN chat_history.updated_at IS '수정 시간';

-- =====================================================
-- 3. 사용자 계정 테이블
-- =====================================================
CREATE TABLE IF NOT EXISTS user_account (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    user_name VARCHAR(255) NOT NULL,
    password VARCHAR(255),
    role VARCHAR(50) NOT NULL, -- CUSTOMER, AGENT
    email VARCHAR(255),
    company_id VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX ux_user_account_login ON user_account(user_id, role, company_id);
CREATE INDEX idx_user_account_role ON user_account(role);
CREATE INDEX idx_user_account_company_id ON user_account(company_id);
CREATE INDEX idx_user_account_active ON user_account(active);

COMMENT ON TABLE user_account IS '사용자 계정 정보';
COMMENT ON COLUMN user_account.user_id IS '로그인 ID';
COMMENT ON COLUMN user_account.user_name IS '사용자 이름';
COMMENT ON COLUMN user_account.password IS '로그인 비밀번호(평문/해시)';
COMMENT ON COLUMN user_account.role IS '역할(CUSTOMER, AGENT)';
COMMENT ON COLUMN user_account.email IS '이메일';
COMMENT ON COLUMN user_account.company_id IS '회사 ID';
COMMENT ON COLUMN user_account.active IS '활성 여부';
COMMENT ON COLUMN user_account.created_at IS '생성 시간';
COMMENT ON COLUMN user_account.updated_at IS '수정 시간';

-- =====================================================
-- 4. 외래키 제약조건 (선택사항)
-- =====================================================
-- 필요시 chat_history의 room_id가 chat_session의 room_id를 참조하도록 설정
-- ALTER TABLE chat_history 
-- ADD CONSTRAINT fk_chat_history_session 
-- FOREIGN KEY (room_id) REFERENCES chat_session(room_id) 
-- ON DELETE CASCADE;

-- =====================================================
-- 5. 파티셔닝 (선택사항 - 대용량 데이터 처리)
-- =====================================================
-- 월별 파티셔닝 예시 (PostgreSQL 10+)
-- chat_history 테이블을 created_at 기준으로 월별 파티셔닝

-- DROP TABLE IF EXISTS chat_history;
-- 
-- CREATE TABLE chat_history (
--     id BIGSERIAL,
--     room_id VARCHAR(100) NOT NULL,
--     sender_id VARCHAR(100) NOT NULL,
--     sender_name VARCHAR(255) NOT NULL,
--     sender_role VARCHAR(50) NOT NULL,
--     message TEXT NOT NULL,
--     message_type VARCHAR(50) NOT NULL,
--     company_id VARCHAR(100),
--     created_at TIMESTAMP NOT NULL DEFAULT NOW(),
--     updated_at TIMESTAMP NOT NULL DEFAULT NOW()
-- ) PARTITION BY RANGE (created_at);
-- 
-- -- 2026년 1월 파티션
-- CREATE TABLE chat_history_2026_01 PARTITION OF chat_history
--     FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
-- 
-- -- 2026년 2월 파티션
-- CREATE TABLE chat_history_2026_02 PARTITION OF chat_history
--     FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
-- 
-- -- 필요에 따라 추가 파티션 생성...

-- =====================================================
-- 6. 샘플 데이터 (테스트용)
-- =====================================================
-- 샘플 사용자 계정
INSERT INTO user_account (user_id, user_name, password, role, email, company_id)
VALUES
    ('agent01', '상담원-01', '1234', 'AGENT'   , 'agent01@aicc.com'  , 'apt001'),
    ('agent02', '상담원-02', '1234', 'AGENT'   , 'agent02@aicc.com'  , 'apt001'),
    ('agent03', '상담원-03', '1234', 'AGENT'   , 'agent03@aicc.com'  , 'apt001'),
    ('cust01' , '홍길철'   , '1234', 'CUSTOMER', 'cust01@example.com', 'apt001'),
    ('cust02' , '홍길수'   , '1234', 'CUSTOMER', 'cust02@example.com', 'apt001'),
    ('cust03' , '홍길동'   , '1234', 'CUSTOMER', 'cust03@example.com', 'apt001');

-- 샘플 세션 데이터
/*
INSERT INTO chat_session (room_id, room_name, customer_id, customer_name, status, company_id)
VALUES 
    ('room-test001', 'user001', 'user001', '홍길동', 'BOT', 'apt001'),
    ('room-test002', 'user002', 'user002', '김철수', 'AGENT', 'apt001');

-- 샘플 이력 데이터
INSERT INTO chat_history (room_id, sender_id, sender_name, sender_role, message, message_type, company_id)
VALUES 
    ('room-test001', 'BOT', 'Bot', 'BOT', '안녕하세요! 무엇을 도와드릴까요?', 'TALK', 'apt001'),
    ('room-test001', 'user001', '홍길동', 'CUSTOMER', '배송 조회를 하고 싶습니다.', 'TALK', 'apt001'),
    ('room-test001', 'BOT', 'Bot', 'BOT', '배송 조회를 도와드리겠습니다. 주문번호를 알려주세요.', 'TALK', 'apt001');
*/
-- =====================================================
-- 7. 유용한 쿼리
-- =====================================================

-- 특정 방의 전체 대화 내역 조회
-- SELECT * FROM chat_history 
-- WHERE room_id = 'room-test001' 
-- ORDER BY created_at ASC;

-- 고객별 상담 이력 조회
-- SELECT * FROM chat_session 
-- WHERE customer_id = 'user001' 
-- ORDER BY started_at DESC;

-- 오늘 진행된 상담 건수
-- SELECT COUNT(*) FROM chat_session 
-- WHERE DATE(started_at) = CURRENT_DATE;

-- 상담원별 처리 건수
-- SELECT assigned_agent, COUNT(*) as session_count
-- FROM chat_session 
-- WHERE assigned_agent IS NOT NULL
-- GROUP BY assigned_agent
-- ORDER BY session_count DESC;

-- 활성 상담 세션 조회
-- SELECT * FROM chat_session 
-- WHERE status != 'CLOSED' 
-- ORDER BY last_activity_at DESC;

-- 30일 이상 오래된 이력 삭제 (정리용)
-- DELETE FROM chat_history 
-- WHERE created_at < NOW() - INTERVAL '30 days';

-- =====================================================
-- 8. 권한 설정
-- =====================================================
-- GRANT ALL PRIVILEGES ON DATABASE aicc_chat TO aicc;
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO aicc;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO aicc;
