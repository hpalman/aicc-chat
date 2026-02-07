package aicc.chat.consts;

public class Constants {
	/**
	 * Redis Hash 구조로 온라인 상담사 정보 저장
	 * Key: chat:agents:{userId}
	 * Hash Fields:
	 *   - userName: 상담사 이름
	 *   - userId: 상담사 ID
	 *   - loginTime: 로그인 시간 (ISO-8601)
	 *   - lastHeartbeat: 마지막 하트비트 시간 (ISO-8601)
	 *   - sessionId: WebSocket 세션 ID (optional)
	 */
	public static final String USER_AGENT_KEY = "chat:user-agent";

	/**
	 * Redis Hash 구조로 온라인 고객 정보 저장
	 * Key: chat:customers:{userId}
	 * Hash Fields:
	 *   - userName: 고객 이름
	 *   - userId: 고객 ID
	 *   - loginTime: 로그인 시간 (ISO-8601)
	 *   - roomId: 채팅방 ID
	 *   - sessionId: WebSocket 세션 ID
	 *   - companyId: 회사 ID
	 */
	public static final String USER_CUSTOMER_KEY = "chat:user-customer";

	/**
	 * Redis Set 구조로 채팅방 멤버 정보 저장
	 * Key: room:mems:{roomId}
	 * Set Members: userId1, userId2, ...
	 */
	public static final String ROOM_MEMBERS_KEY_PREFIX = "chat:room-member:";

	public static final String ROOM_INFO_KEY        = "chat:room-info";
	public static final String ROOM_INFO_KEY_PREFIX = "chat:room-info:"; // room metadata (Hash)

}
