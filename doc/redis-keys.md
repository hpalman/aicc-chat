# Redis 키 구조 (REDIS_ONLY 모드)

이 문서는 `RedisRoomRepository`에서 사용하는 Redis 키 구조를 정리합니다.

## 전역 인덱스

| 키 | 타입 | 설명 |
| --- | --- | --- |
| `chat:rooms` | Set | 전체 `roomId` 목록을 보관하는 인덱스 |

## 방 단위 키

| 키 패턴 | 타입 | 설명 |
| --- | --- | --- |
| `chat:room:{roomId}` | Set | 방 멤버 목록(고객/상담원 ID) |
| `chat:room:{roomId}:name` | String | 방 이름(없으면 미저장) |
| `chat:room:{roomId}:createdAt` | String | 방 생성 시간(밀리초) |
| `chat:room:{roomId}:lastActivity` | String | 마지막 활동 시간(밀리초) |
| `chat:room:{roomId}:mode` | String | 방 상태(BOT/WAITING/AGENT/CLOSED 등) |
| `chat:room:{roomId}:assignedAgent` | String | 배정된 상담원 이름 |

## 생성/갱신 흐름 요약

| 동작 | 갱신되는 키 |
| --- | --- |
| 방 생성 | `chat:rooms`, `chat:room:{roomId}`, `:name`, `:createdAt`, `:lastActivity` |
| 멤버 추가 | `chat:room:{roomId}`, `chat:rooms` |
| 라우팅 상태 변경 | `chat:room:{roomId}:mode` |
| 상담원 배정 | `chat:room:{roomId}:assignedAgent`, `chat:room:{roomId}:mode`, `:lastActivity` |
| 활동 시간 갱신 | `chat:room:{roomId}:lastActivity` |
| 방 삭제 | 위 방 단위 키 전체 + `chat:rooms`에서 제거 |

## 확인 예시 (redis-cli)

```
SMEMBERS chat:rooms
SMEMBERS chat:room:{roomId}
GET chat:room:{roomId}:name
GET chat:room:{roomId}:createdAt
GET chat:room:{roomId}:lastActivity
GET chat:room:{roomId}:mode
GET chat:room:{roomId}:assignedAgent
```
