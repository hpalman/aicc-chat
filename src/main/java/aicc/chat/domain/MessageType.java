package aicc.chat.domain;

public enum MessageType {
    ENTER,
    TALK,
    LEAVE,
    JOIN, // 고객 입장
    HANDOFF, // 상담원 연결 요청 추가
    CANCEL_HANDOFF, // 상담원 연결 요청 취소 추가
    INTERVENE, // 상담원 개입 알림
    CUSTOMER_DISCONNECTED, // 고객 연결 해제 알림 (상담원에게 전송)
    CUSTOMER_LEFT // 고객 퇴장 알림 (상담원에게 전송)
}
