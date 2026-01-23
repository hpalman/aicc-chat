package aicc.chat.domain;

/**
 * 메시지 발신자의 역할을 정의하는 Enum
 */
public enum UserRole {
    CUSTOMER, // 일반 고객
    AGENT,    // 상담원
    BOT,      // 챗봇
    SYSTEM    // 시스템 (알림 등)
}

