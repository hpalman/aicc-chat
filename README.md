# AICC Chat System

실시간 채팅 및 AI 챗봇 통합 시스템

## 📋 목차
- [프로젝트 개요](#프로젝트-개요)
- [주요 기능](#주요-기능)
- [시스템 아키텍처](#시스템-아키텍처)
- [설치 가이드](#설치-가이드)
- [Botpress 통합](#botpress-통합)
- [사용 방법](#사용-방법)

---

## 프로젝트 개요

AICC Chat은 Spring Boot 기반의 실시간 채팅 시스템으로, WebSocket을 통한 양방향 통신과 Botpress를 활용한 AI 챗봇 기능을 제공합니다.

### 기술 스택
- **Backend**: Spring Boot 3.x, Java 17
- **Real-time**: WebSocket (STOMP)
- **Message Broker**: RabbitMQ
- **Cache/Session**: Redis
- **AI Chatbot**: Botpress v12
- **Database**: PostgreSQL (Botpress용)

---

## 주요 기능

### 1. 실시간 채팅
- WebSocket 기반 양방향 통신
- 다중 채팅방 지원
- 사용자 입장/퇴장 알림
- 메시지 브로드캐스팅

### 2. AI 챗봇 통합
- Botpress v12 통합
- 자연어 이해 (NLU)
- 워크플로우 기반 대화 관리
- 다중 채널 지원

### 3. 확장 가능한 아키텍처
- Redis를 통한 세션 관리
- RabbitMQ를 통한 메시지 큐잉
- 마이크로서비스 지향 설계

---

## 시스템 아키텍처

```
┌─────────────┐     WebSocket      ┌──────────────────┐
│   Client    │ ←─────────────────→ │  Spring Boot     │
│  (Browser)  │                     │  Application     │
└─────────────┘                     └──────────────────┘
                                            │
                    ┌───────────────────────┼───────────────────────┐
                    │                       │                       │
              ┌─────▼─────┐          ┌─────▼─────┐          ┌─────▼─────┐
              │   Redis   │          │ RabbitMQ  │          │ Botpress  │
              │  (Cache)  │          │  (Queue)  │          │   (AI)    │
              └───────────┘          └───────────┘          └───────────┘
```

---

## 설치 가이드

### 사전 요구사항
- Java 17+
- Gradle 7.x+
- Docker & Docker Compose
- Redis
- RabbitMQ

### 1. 저장소 클론
```bash
git clone <repository-url>
cd aicc-chat
```

### 2. 의존성 설치 및 빌드
```bash
./gradlew clean build
```

### 3. Docker Compose로 인프라 시작
```bash
# Redis & RabbitMQ 시작
docker-compose up -d redis rabbitmq
```

### 4. 애플리케이션 실행
```bash
./gradlew bootRun
```

### 5. 접속 확인
- 웹 클라이언트: `http://localhost:8080/websocket-client.html`
- 관리자 클라이언트: `http://localhost:8080/admin-client.html`

---

## Botpress 통합

### 빠른 시작
Botpress v12를 192.168.133.132 서버 (Rocky Linux 9.6)에 설치하려면:

```bash
# Rocky Linux 서버 (자동 설치)
chmod +x setup-botpress.sh
./setup-botpress.sh

# Windows PowerShell (원격 설치)
.\setup-botpress.ps1
```

### 상세 가이드
- 🐧 **Rocky Linux 전용 가이드**: [ROCKY_LINUX_SETUP.md](./ROCKY_LINUX_SETUP.md) ⭐ 추천
- 📚 **전체 설치 가이드**: [BOTPRESS_INSTALLATION_GUIDE.md](./BOTPRESS_INSTALLATION_GUIDE.md)
- 🚀 **빠른 시작**: [BOTPRESS_QUICK_START.md](./BOTPRESS_QUICK_START.md)
- 🔧 **문제 해결**: [BOTPRESS_TROUBLESHOOTING.md](./BOTPRESS_TROUBLESHOOTING.md)
- 🐳 **Docker Compose**: [docker-compose.botpress.yml](./docker-compose.botpress.yml)

### Botpress 접속 정보
```
URL: http://192.168.133.132:3000
초기 이메일: admin@botpress.local
초기 비밀번호: Admin@2024!
```

### 통합 설정
`application.yml`에서 Botpress 설정:
```yaml
botpress:
  server-url: http://192.168.133.132:3000
  bot-id: your-bot-id
  timeout: 5000
```

---

## 사용 방법

### 채팅방 생성
```bash
curl -X POST http://localhost:8080/api/chat/rooms \
  -H "Content-Type: application/json" \
  -d '{"name": "General Chat"}'
```

### WebSocket 연결
```javascript
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
    console.log('Connected: ' + frame);
    
    // 채팅방 구독
    stompClient.subscribe('/topic/room/room-id', function(message) {
        console.log('Received: ' + message.body);
    });
    
    // 메시지 전송
    stompClient.send('/app/chat.send/room-id', {}, JSON.stringify({
        sender: 'user1',
        content: 'Hello!',
        type: 'CHAT'
    }));
});
```

### 봇과 대화
```bash
curl -X POST http://localhost:8080/api/bot/message \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user123",
    "message": "안녕하세요"
  }'
```

---

## 프로젝트 구조

```
aicc-chat/
├── src/main/java/aicc/
│   ├── bot/                    # 챗봇 통합
│   │   ├── botpress/          # Botpress 서비스
│   │   ├── michat/            # MiChat 구현
│   │   └── web/               # Bot API 컨트롤러
│   └── chat/                   # 채팅 기능
│       ├── config/            # 설정
│       ├── controller/        # REST & WebSocket 컨트롤러
│       ├── domain/            # 도메인 모델
│       ├── service/           # 비즈니스 로직
│       └── websocket/         # WebSocket 이벤트
├── frontend/                   # 프론트엔드 클라이언트
│   ├── websocket-client.html # 일반 사용자 클라이언트
│   └── admin-client.html      # 관리자 클라이언트
├── docker-compose.yml         # 인프라 구성
├── docker-compose.botpress.yml # Botpress 전용
├── setup-botpress.sh          # Botpress 설치 스크립트 (Linux)
├── setup-botpress.ps1         # Botpress 설치 스크립트 (Windows)
├── BOTPRESS_INSTALLATION_GUIDE.md  # Botpress 상세 가이드
├── BOTPRESS_QUICK_START.md    # Botpress 빠른 시작
└── build.gradle               # Gradle 빌드 설정
```

---

## 환경 설정

### application.yml
```yaml
spring:
  redis:
    host: localhost
    port: 6379
  rabbitmq:
    host: localhost
    port: 5672

app:
  system:
    mode: REDIS_ONLY  # IN_MEMORY, REDIS_ONLY, REDIS_RABBIT

botpress:
  server-url: http://192.168.133.132:3000
  bot-id: customer-service-bot
```

### 시스템 모드
- **IN_MEMORY**: 메모리 기반 (개발용)
- **REDIS_ONLY**: Redis만 사용
- **REDIS_RABBIT**: Redis + RabbitMQ (프로덕션)

---

## API 문서

상세한 API 명세는 [API_SPEC.md](./API_SPEC.md)를 참조하세요.

### 주요 엔드포인트
- `GET /api/chat/rooms` - 채팅방 목록
- `POST /api/chat/rooms` - 채팅방 생성
- `POST /api/bot/message` - 봇에게 메시지 전송
- `WebSocket /ws` - WebSocket 연결

---

## 개발 가이드

### 로컬 개발 환경 설정
```bash
# 1. Redis & RabbitMQ 시작
docker-compose up -d redis rabbitmq

# 2. 애플리케이션 실행 (개발 모드)
./gradlew bootRun --args='--spring.profiles.active=dev'

# 3. 핫 리로드 활성화
./gradlew bootRun --continuous
```

### 테스트 실행
```bash
# 전체 테스트
./gradlew test

# 특정 테스트
./gradlew test --tests ChatControllerTest
```

---

## 배포

### JAR 빌드
```bash
./gradlew bootJar
java -jar build/libs/aicc-chat-0.0.1-SNAPSHOT.jar
```

### Docker 이미지 빌드
```bash
docker build -t aicc-chat:latest .
docker run -p 8080:8080 aicc-chat:latest
```

---

## 문제 해결

### Redis 연결 오류
```bash
# Redis 상태 확인
docker-compose ps redis
docker-compose logs redis

# Redis 재시작
docker-compose restart redis
```

### RabbitMQ 연결 오류
```bash
# RabbitMQ 관리 콘솔
http://localhost:15672
# 기본 계정: guest/guest
```

### Botpress 연결 오류
```bash
# Botpress 상태 확인
curl http://192.168.133.132:3000/status

# Botpress 로그 확인
cd /opt/botpress
docker-compose logs -f botpress
```

---

## 기여 가이드

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 라이선스

이 프로젝트는 [라이선스 유형]에 따라 라이선스가 부여됩니다.

---

## 연락처

- 프로젝트 관리자: [이름]
- 이메일: [이메일]
- 프로젝트 링크: [GitHub URL]

---

## 감사의 말

- [Botpress](https://botpress.com/) - AI 챗봇 플랫폼
- [Spring Boot](https://spring.io/projects/spring-boot) - 애플리케이션 프레임워크
- [RabbitMQ](https://www.rabbitmq.com/) - 메시지 브로커
- [Redis](https://redis.io/) - 인메모리 데이터 저장소
