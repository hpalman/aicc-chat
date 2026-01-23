# Botpress v12 Docker 설치 가이드

## 목차
1. [개요](#개요)
2. [사전 요구사항](#사전-요구사항)
3. [설치 단계](#설치-단계)
4. [Botpress 접속 및 초기 설정](#botpress-접속-및-초기-설정)
5. [워크플로우 생성](#워크플로우-생성)
6. [문제 해결](#문제-해결)

---

## 개요

이 가이드는 **192.168.133.132** 서버에 Botpress v12를 Docker를 통해 설치하는 방법을 상세히 설명합니다.

### Botpress v12 선택 이유
- **안정성**: v12는 프로덕션 환경에서 검증된 안정화 버전입니다
- **커뮤니티 지원**: 풍부한 문서와 커뮤니티 지원
- **기능 완성도**: 워크플로우, NLU, 채널 통합 등 핵심 기능이 완성되어 있습니다

### 권장 Docker 이미지
```
botpress/server:v12_latest
```
또는 특정 버전:
```
botpress/server:12.26.11
```

---

## 사전 요구사항

### 1. 서버 환경
- **서버 IP**: 192.168.133.132
- **OS**: Rocky Linux 9.6 (Blue Onyx) - RHEL 9 호환
- **최소 사양**:
  - CPU: 2 cores 이상
  - RAM: 4GB 이상
  - 디스크: 20GB 이상 여유 공간

### 2. 필수 소프트웨어
```bash
# Docker 설치 확인
docker --version
# Docker version 20.10.0 이상 권장

# Docker Compose 설치 확인
docker-compose --version
# Docker Compose version 1.29.0 이상 권장
```

### 3. Docker 및 Docker Compose 설치 (미설치 시)

#### Docker 설치 (Rocky Linux 9.6)
```bash
# 이전 버전 제거
sudo dnf remove -y docker \
                  docker-client \
                  docker-client-latest \
                  docker-common \
                  docker-latest \
                  docker-latest-logrotate \
                  docker-logrotate \
                  docker-engine \
                  podman \
                  runc

# 필수 패키지 설치
sudo dnf install -y dnf-plugins-core

# Docker 공식 저장소 추가
sudo dnf config-manager --add-repo https://download.docker.com/linux/rhel/docker-ce.repo

# Docker 설치
sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Docker 서비스 시작 및 자동 시작 설정
sudo systemctl start docker
sudo systemctl enable docker

# 현재 사용자를 docker 그룹에 추가 (sudo 없이 docker 명령 사용)
sudo usermod -aG docker $USER

# Docker 설치 확인
sudo docker --version

# 변경사항 적용을 위해 로그아웃 후 재로그인 필요
# 또는 다음 명령으로 그룹 변경 즉시 적용
newgrp docker
```

#### Docker Compose 설치 (standalone 버전)
```bash
# 최신 버전 다운로드
sudo curl -L "https://github.com/docker/compose/releases/download/v2.24.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose

# 실행 권한 부여
sudo chmod +x /usr/local/bin/docker-compose

# 설치 확인
docker-compose --version
```

### 4. 방화벽 설정 (Rocky Linux - firewalld)
```bash
# firewalld 상태 확인
sudo systemctl status firewalld

# firewalld가 실행 중이 아니면 시작
sudo systemctl start firewalld
sudo systemctl enable firewalld

# Botpress 포트 개방 (3000)
sudo firewall-cmd --permanent --add-port=3000/tcp

# PostgreSQL 포트 개방 (외부 접속 필요시)
sudo firewall-cmd --permanent --add-port=5432/tcp

# Duckling 포트 개방 (선택사항)
sudo firewall-cmd --permanent --add-port=8000/tcp

# 방화벽 규칙 적용
sudo firewall-cmd --reload

# 방화벽 상태 확인
sudo firewall-cmd --list-all
```

### 5. SELinux 설정 (Rocky Linux)
```bash
# SELinux 상태 확인
getenforce

# SELinux가 Enforcing 모드인 경우, Docker 컨테이너 허용
# 옵션 1: SELinux를 Permissive 모드로 변경 (권장하지 않음, 테스트용)
sudo setenforce 0

# 옵션 2: SELinux 정책 추가 (권장)
# Docker 볼륨에 대한 컨텍스트 설정
sudo chcon -Rt svirt_sandbox_file_t /opt/botpress

# 영구적으로 Permissive 모드로 설정하려면 (프로덕션에서는 권장하지 않음)
# sudo vi /etc/selinux/config
# SELINUX=permissive 로 변경

# 또는 SELinux를 유지하면서 Docker 사용
sudo setsebool -P container_manage_cgroup on
```

### 6. 텍스트 에디터 설치 (선택사항)
```bash
# vi/vim이 익숙하지 않다면 nano 설치
sudo dnf install -y nano

# 또는 더 사용하기 쉬운 에디터들
sudo dnf install -y vim-enhanced
```

---

## 설치 단계

### 1. 작업 디렉토리 생성

```bash
# 192.168.133.132 서버에 SSH 접속
ssh user@192.168.133.132

# Botpress 설치 디렉토리 생성
sudo mkdir -p /opt/botpress
cd /opt/botpress

# 권한 설정
sudo chown -R $USER:$USER /opt/botpress
```

### 2. Docker Compose 파일 생성

`docker-compose.yml` 파일을 생성합니다:

```bash
# vi 에디터로 파일 생성
vi docker-compose.yml
```

**vi 에디터 사용법:**
- `i` 키를 눌러 입력 모드로 전환
- 아래 내용을 복사하여 붙여넣기 (Shift+Insert 또는 마우스 우클릭)
- `ESC` 키를 눌러 명령 모드로 전환
- `:wq` 입력 후 Enter (저장 후 종료)
- 취소하려면 `:q!` (저장하지 않고 종료)

아래 내용을 입력합니다:

```yaml
version: '3.8'

services:
  # PostgreSQL 데이터베이스
  postgres:
    image: postgres:13-alpine
    container_name: botpress-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: botpress
      POSTGRES_USER: botpress
      POSTGRES_PASSWORD: botpress_secure_password_2024
      PGDATA: /var/lib/postgresql/data/pgdata
    volumes:
      - postgres_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    networks:
      - botpress-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U botpress"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Botpress v12 서버
  botpress:
    image: botpress/server:12.26.11
    container_name: botpress-server
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      # 데이터베이스 설정
      DATABASE_URL: postgres://botpress:botpress_secure_password_2024@postgres:5432/botpress
      
      # Botpress 설정
      BP_HOST: 0.0.0.0
      BP_PORT: 3000
      
      # 외부 URL 설정 (실제 서버 IP로 변경)
      EXTERNAL_URL: http://192.168.133.132:3000
      
      # 프로덕션 모드
      BP_PRODUCTION: "true"
      
      # 로그 레벨
      VERBOSITY_LEVEL: "info"
      
      # 모듈 설정
      BP_MODULE_NLU_DUCKLINGURL: http://duckling:8000
      
      # 보안 설정 (초기 관리자 계정)
      SUPERADMIN_EMAIL: admin@botpress.local
      SUPERADMIN_PASSWORD: Admin@2024!
    ports:
      - "3000:3000"
    volumes:
      - botpress_data:/botpress/data
      - ./botpress.config.json:/botpress/data/global/botpress.config.json
    networks:
      - botpress-network
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:3000/status"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

  # Duckling (날짜/시간 등 엔티티 추출용)
  duckling:
    image: rasa/duckling:latest
    container_name: botpress-duckling
    restart: unless-stopped
    ports:
      - "8000:8000"
    networks:
      - botpress-network

networks:
  botpress-network:
    driver: bridge

volumes:
  postgres_data:
    driver: local
  botpress_data:
    driver: local
```

### 3. Botpress 설정 파일 생성 (선택사항)

기본 설정을 커스터마이즈하려면 `botpress.config.json` 파일을 생성합니다:

```bash
# vi 에디터로 파일 생성
vi botpress.config.json
```

**vi 에디터 사용:**
- `i` 키를 눌러 입력 모드로 전환
- 아래 JSON 내용을 붙여넣기
- `ESC` 키 → `:wq` 입력 후 Enter (저장 후 종료)

```json
{
  "$schema": "../../assets/config-schema.json",
  "version": "12.26.11",
  "appSecret": "change-this-to-random-string-for-production",
  "httpServer": {
    "host": "0.0.0.0",
    "port": 3000,
    "backlog": 511,
    "bodyLimit": "100mb",
    "cors": {
      "enabled": true,
      "origin": "*"
    }
  },
  "database": {
    "type": "postgres",
    "url": "postgres://botpress:botpress_secure_password_2024@postgres:5432/botpress"
  },
  "logs": {
    "level": "info",
    "fileOutput": {
      "enabled": true,
      "folder": "./",
      "maxFileSize": 10000000
    }
  },
  "modules": [
    {
      "location": "MODULES_ROOT/analytics",
      "enabled": true
    },
    {
      "location": "MODULES_ROOT/basic-skills",
      "enabled": true
    },
    {
      "location": "MODULES_ROOT/builtin",
      "enabled": true
    },
    {
      "location": "MODULES_ROOT/channel-web",
      "enabled": true
    },
    {
      "location": "MODULES_ROOT/nlu",
      "enabled": true
    },
    {
      "location": "MODULES_ROOT/qna",
      "enabled": true
    }
  ],
  "pro": {
    "enabled": false
  },
  "superAdmins": {
    "email": "admin@botpress.local",
    "password": "Admin@2024!"
  }
}
```

### 4. Docker Compose 실행

```bash
# 백그라운드에서 컨테이너 시작
docker-compose up -d

# 로그 확인
docker-compose logs -f botpress

# 모든 서비스 상태 확인
docker-compose ps
```

예상 출력:
```
NAME                  COMMAND                  SERVICE             STATUS              PORTS
botpress-duckling     "/entrypoint.sh"         duckling            running             0.0.0.0:8000->8000/tcp
botpress-postgres     "docker-entrypoint.s…"   postgres            running (healthy)   0.0.0.0:5432->5432/tcp
botpress-server       "docker-entrypoint.s…"   botpress            running (healthy)   0.0.0.0:3000->3000/tcp
```

### 5. 설치 확인

```bash
# Botpress 서버 상태 확인
curl http://192.168.133.132:3000/status

# 컨테이너 로그 확인
docker logs botpress-server

# PostgreSQL 연결 확인
docker exec -it botpress-postgres psql -U botpress -d botpress -c "SELECT version();"
```

---

## Botpress 접속 및 초기 설정

### 1. 웹 브라우저로 접속

```
http://192.168.133.132:3000
```

### 2. 초기 관리자 계정 로그인

- **이메일**: `admin@botpress.local`
- **비밀번호**: `Admin@2024!`

> ⚠️ **보안 주의**: 프로덕션 환경에서는 반드시 비밀번호를 변경하세요!

### 3. 비밀번호 변경

1. 로그인 후 우측 상단 프로필 아이콘 클릭
2. **Profile Settings** 선택
3. **Change Password** 에서 새 비밀번호 설정

### 4. 첫 번째 봇 생성

1. 대시보드에서 **Create Bot** 클릭
2. 봇 정보 입력:
   - **Bot Name**: 예) `customer-service-bot`
   - **Bot ID**: 예) `customer-service-bot` (자동 생성)
   - **Template**: `Empty Bot` 또는 `Small Talk` 선택
3. **Create Bot** 클릭

---

## 워크플로우 생성

### 1. Flow Editor 접근

1. 생성한 봇 카드에서 **Open in Studio** 클릭
2. 좌측 메뉴에서 **Flows** 선택
3. `main.flow.json` 또는 **Create Flow** 클릭

### 2. 기본 워크플로우 구조

```
Entry (시작) → Node 1 (처리) → Node 2 (응답) → End (종료)
```

### 3. 간단한 인사 워크플로우 예제

#### Step 1: Entry Node 설정
- Flow Editor에서 **Entry** 노드 더블클릭
- **On Receive** 트리거 설정

#### Step 2: 조건 분기 추가
1. **Add Node** → **Standard Node** 생성
2. 노드 이름: `check-greeting`
3. **Transitions** 탭에서:
   ```javascript
   // 인사말 감지
   if (event.preview && event.preview.toLowerCase().includes('안녕')) {
     return 'greeting';
   }
   return 'default';
   ```

#### Step 3: 응답 노드 추가
1. **Add Node** → **Standard Node** 생성
2. 노드 이름: `send-greeting`
3. **On Enter** 액션 추가:
   - **Action Type**: `Say`
   - **Content Type**: `Text`
   - **Text**: 
   ```
   안녕하세요! 무엇을 도와드릴까요?
   ```

#### Step 4: 노드 연결
- `check-greeting` 노드의 `greeting` transition을 `send-greeting`에 연결
- `send-greeting` 노드를 **End** 노드에 연결

### 4. NLU (자연어 이해) 설정

#### Intent 생성
1. 좌측 메뉴에서 **NLU** → **Intents** 선택
2. **Create Intent** 클릭
3. Intent 정보 입력:
   - **Intent Name**: `greeting`
   - **Utterances** (학습 문장):
     ```
     안녕하세요
     안녕
     반갑습니다
     하이
     헬로
     좋은 아침입니다
     ```
4. **Save** 클릭

#### Entity 생성 (선택사항)
1. **NLU** → **Entities** 선택
2. **Create Entity** 클릭
3. 예: `product-name` 엔티티 생성

#### Flow에서 Intent 사용
1. `check-greeting` 노드 수정
2. **Transitions** 탭:
   ```javascript
   // NLU Intent 사용
   if (event.nlu && event.nlu.intent.name === 'greeting') {
     return 'greeting';
   }
   return 'default';
   ```

### 5. 고급 워크플로우 기능

#### 변수 사용
```javascript
// 사용자 이름 저장
temp.userName = event.state.user.name || '고객님';

// 변수 사용한 응답
bp.cms.renderElement('builtin_text', {
  text: `안녕하세요, ${temp.userName}!`
}, event);
```

#### 외부 API 호출
```javascript
// Action 노드에서 HTTP 요청
const axios = require('axios');

const response = await axios.get('http://192.168.133.132:8080/api/chat/rooms');
temp.chatRooms = response.data;
```

#### 조건부 분기
```javascript
// 시간대별 인사
const hour = new Date().getHours();

if (hour < 12) {
  return 'morning';
} else if (hour < 18) {
  return 'afternoon';
} else {
  return 'evening';
}
```

### 6. 워크플로우 테스트

1. 우측 하단의 **Emulator** 아이콘 클릭
2. 채팅창에서 메시지 입력하여 테스트
3. **Debug** 탭에서 실행 흐름 확인

### 7. 워크플로우 배포

1. 우측 상단의 **Publish** 버튼 클릭
2. 변경사항 확인 후 **Publish** 확인
3. 봇이 자동으로 재시작되며 새 워크플로우 적용

---

## 기존 AICC Chat 시스템과 통합

### 1. Botpress API 엔드포인트

```
POST http://192.168.133.132:3000/api/v1/bots/{botId}/converse/{userId}
```

### 2. 통합 예제 (Java Spring)

기존 `BotpressService.java` 수정:

```java
@Service
public class BotpressService implements ChatBot {
    
    private final WebClient webClient;
    
    // Botpress 서버 URL 업데이트
    private static final String BOTPRESS_URL = "http://192.168.133.132:3000";
    private static final String BOT_ID = "customer-service-bot";
    
    public BotpressService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            .baseUrl(BOTPRESS_URL)
            .build();
    }
    
    @Override
    public Mono<String> sendMessage(ChatBotRequest request) {
        String endpoint = String.format("/api/v1/bots/%s/converse/%s", 
            BOT_ID, request.getUserId());
            
        Map<String, Object> payload = Map.of(
            "type", "text",
            "text", request.getMessage()
        );
        
        return webClient.post()
            .uri(endpoint)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(BotpressResponse.class)
            .map(response -> response.getResponses().get(0).getText())
            .onErrorResume(e -> Mono.just("봇 응답 오류: " + e.getMessage()));
    }
}
```

### 3. application.yml 설정 추가

```yaml
botpress:
  server-url: http://192.168.133.132:3000
  bot-id: customer-service-bot
  timeout: 5000
```

---

## 문제 해결

### 1. 컨테이너가 시작되지 않는 경우

```bash
# 로그 확인
docker-compose logs botpress

# 컨테이너 재시작
docker-compose restart botpress

# 완전히 재생성
docker-compose down
docker-compose up -d
```

### 2. PostgreSQL 연결 오류

```bash
# PostgreSQL 컨테이너 상태 확인
docker-compose ps postgres

# PostgreSQL 로그 확인
docker-compose logs postgres

# 데이터베이스 연결 테스트
docker exec -it botpress-postgres psql -U botpress -d botpress
```

### 3. 포트 충돌

```bash
# 포트 사용 확인
sudo netstat -tulpn | grep :3000

# 다른 포트 사용 (docker-compose.yml 수정)
ports:
  - "3001:3000"  # 외부:내부
```

### 4. 메모리 부족

```bash
# 메모리 사용량 확인
docker stats

# docker-compose.yml에 메모리 제한 추가
services:
  botpress:
    deploy:
      resources:
        limits:
          memory: 2G
        reservations:
          memory: 1G
```

### 5. 로그 확인 명령어

```bash
# 실시간 로그 확인
docker-compose logs -f botpress

# 최근 100줄 로그
docker-compose logs --tail=100 botpress

# 특정 시간 이후 로그
docker-compose logs --since 30m botpress
```

### 6. 데이터 백업

```bash
# PostgreSQL 데이터 백업
docker exec botpress-postgres pg_dump -U botpress botpress > botpress_backup_$(date +%Y%m%d).sql

# Botpress 데이터 볼륨 백업
docker run --rm -v botpress_botpress_data:/data -v $(pwd):/backup alpine tar czf /backup/botpress_data_$(date +%Y%m%d).tar.gz /data
```

### 7. 데이터 복원

```bash
# PostgreSQL 데이터 복원
cat botpress_backup_20241222.sql | docker exec -i botpress-postgres psql -U botpress -d botpress

# Botpress 데이터 볼륨 복원
docker run --rm -v botpress_botpress_data:/data -v $(pwd):/backup alpine tar xzf /backup/botpress_data_20241222.tar.gz -C /
```

---

## 유용한 명령어 모음

### Docker 관리

```bash
# 모든 컨테이너 시작
docker-compose up -d

# 모든 컨테이너 중지
docker-compose stop

# 모든 컨테이너 중지 및 제거
docker-compose down

# 볼륨까지 완전히 제거 (주의!)
docker-compose down -v

# 특정 서비스만 재시작
docker-compose restart botpress

# 로그 확인
docker-compose logs -f

# 컨테이너 내부 접속
docker exec -it botpress-server bash
```

### Botpress 관리

```bash
# Botpress CLI 접근
docker exec -it botpress-server /bin/bash

# 봇 목록 확인
docker exec botpress-server ls -la /botpress/data/bots/

# 설정 파일 확인
docker exec botpress-server cat /botpress/data/global/botpress.config.json
```

---

## 보안 권장사항

### 1. 비밀번호 변경
- 초기 관리자 비밀번호 즉시 변경
- PostgreSQL 비밀번호 강력한 것으로 변경

### 2. 방화벽 설정
```bash
# 필요한 포트만 개방
sudo ufw allow from 192.168.133.0/24 to any port 3000
sudo ufw allow from 192.168.133.0/24 to any port 5432
```

### 3. HTTPS 설정 (프로덕션 환경)
- Nginx 리버스 프록시 사용
- Let's Encrypt SSL 인증서 적용

### 4. 정기 백업
```bash
# Cron 작업 추가
crontab -e

# 매일 새벽 2시 백업
0 2 * * * cd /opt/botpress && docker exec botpress-postgres pg_dump -U botpress botpress > /backup/botpress_$(date +\%Y\%m\%d).sql
```

---

## 추가 리소스

### 공식 문서
- Botpress v12 문서: https://v12.botpress.com/docs
- Docker Hub: https://hub.docker.com/r/botpress/server

### 커뮤니티
- Botpress 포럼: https://forum.botpress.com/
- GitHub: https://github.com/botpress/botpress

---

## 버전 정보

- **Botpress**: v12.26.11
- **PostgreSQL**: 13-alpine
- **Duckling**: latest
- **작성일**: 2024-12-22
- **작성자**: AICC Chat 개발팀

---

## 라이선스

Botpress v12는 AGPL-3.0 라이선스를 따릅니다.
상용 라이선스가 필요한 경우 Botpress 공식 웹사이트를 참조하세요.

