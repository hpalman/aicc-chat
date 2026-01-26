# PostgreSQL 쿼리 로그 설정 가이드

## 개요

이 프로젝트는 MyBatis 인터셉터를 사용하여 모든 SQL 쿼리를 로그로 출력합니다.

## 로그 출력 내용

### 1. 실행되는 모든 SQL 쿼리
- SELECT, INSERT, UPDATE, DELETE 쿼리
- 실제 바인딩된 파라미터 값 포함
- 실행 시간 측정

### 2. 로그 포맷

```
===========================================================================================================
[ 2026-01-23 15:30:45.123 ]
[ Mapper Method ] : aicc.chat.mapper.ChatHistoryMapper.insertChatHistory
[ Execution Time ] : 15ms
[ SQL ] :
INSERT INTO chat_history (
    room_id,
    sender_id,
    sender_name,
    sender_role,
    message,
    message_type,
    company_id,
    created_at,
    updated_at
) VALUES (
    'room-12345678',
    'user001',
    '홍길동',
    'CUSTOMER',
    '안녕하세요',
    'TALK',
    'apt001',
    '2026-01-23 15:30:45.123',
    '2026-01-23 15:30:45.123'
)
===========================================================================================================
```

## 로그 레벨 조정

### application.yml 설정

```yaml
logging:
  level:
    # MyBatis SQL 로깅 (trace = 상세, debug = 보통, info = 기본)
    aicc.chat.mapper: trace
    # 애플리케이션 로깅
    aicc.chat: debug
```

### 로그 레벨별 출력

- **INFO**: SQL 쿼리만 출력 (기본값, 권장)
- **DEBUG**: SQL + MyBatis 내부 로그
- **TRACE**: 모든 상세 정보

## 구현 방식

### 1. MyBatisSqlLogger (인터셉터)

`src/main/java/aicc/chat/config/MyBatisSqlLogger.java`

- MyBatis Interceptor를 구현
- SQL 실행 전후에 가로채서 로그 출력
- 파라미터 바인딩 처리
- 실행 시간 측정

### 2. MyBatisConfig (설정)

`src/main/java/aicc/chat/config/MyBatisConfig.java`

- SqlSessionFactory에 인터셉터 등록
- MyBatis 전역 설정

## 로그 확인 방법

### 1. 콘솔 출력

애플리케이션 실행 시 콘솔에 SQL 로그가 자동으로 출력됩니다.

```bash
./gradlew bootRun
```

### 2. 파일 로그 (선택사항)

필요시 logback-spring.xml을 추가하여 파일로 저장할 수 있습니다.

## 로그 비활성화

SQL 쿼리 로그를 비활성화하려면:

### application.yml 수정

```yaml
logging:
  level:
    aicc.chat.mapper: info  # trace에서 info로 변경
```

또는 MyBatisConfig.java에서 인터셉터 등록 주석 처리:

```java
// sessionFactory.setPlugins(new MyBatisSqlLogger());
```

## 주의사항

1. **운영 환경**
   - 로그 레벨을 `info`로 설정 권장
   - 대량의 쿼리 발생 시 성능 영향 가능

2. **개발 환경**
   - 로그 레벨을 `info`로 설정하여 쿼리 확인
   - 디버깅 시 유용

3. **민감 정보**
   - 비밀번호 등 민감한 데이터는 로그에 노출되지 않도록 주의
   - 필요시 마스킹 로직 추가

## 예제

### 채팅 메시지 저장 시 로그

```sql
INSERT INTO chat_history (
    room_id, sender_id, sender_name, sender_role, 
    message, message_type, company_id, created_at, updated_at
) VALUES (
    'room-abc123', 'user001', '홍길동', 'CUSTOMER',
    '배송 조회 부탁드립니다.', 'TALK', 'apt001', 
    '2026-01-23 15:30:45.123', '2026-01-23 15:30:45.123'
)
```

### 채팅 이력 조회 시 로그

```sql
SELECT
    id, room_id, sender_id, sender_name, sender_role,
    message, message_type, company_id, created_at, updated_at
FROM chat_history
WHERE room_id = 'room-abc123'
ORDER BY created_at ASC, id ASC
```

## 문제 해결

### 로그가 출력되지 않을 때

1. **로그 레벨 확인**
   ```yaml
   logging:
     level:
       aicc.chat.mapper: info  # trace 또는 debug로 변경
   ```

2. **인터셉터 등록 확인**
   - MyBatisConfig.java에서 `setPlugins()` 호출 확인

3. **MyBatis 스캔 확인**
   - AiccChatApplication.java에 `@MapperScan("aicc.chat.mapper")` 확인

### 로그가 너무 많을 때

```yaml
logging:
  level:
    aicc.chat.mapper: warn  # 경고 이상만 출력
```

## 추가 기능

필요시 다음 기능을 추가할 수 있습니다:

1. **느린 쿼리만 로그**
   - 특정 시간(예: 1000ms) 이상 걸린 쿼리만 출력

2. **쿼리 통계**
   - 실행 횟수, 평균 시간 등 수집

3. **쿼리 히스토리**
   - 별도 테이블에 쿼리 실행 이력 저장
