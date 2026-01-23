package aicc.chat.domain;

public enum MessageType {
    ENTER,
    TALK,
    LEAVE,
    JOIN,
    HANDOFF, // 상담원 연결 요청 추가
    CANCEL_HANDOFF // 상담원 연결 요청 취소 추가
}
