# Botpress 문제 해결 가이드

## 🔧 일반적인 오류 및 해결 방법

---

## 오류 1: 환경 변수 Deprecated 경고

### 증상
```
ConfigProvider (Deprecated) use standard syntax to set config from environment variable: 
BP_PORT ==> BP_CONFIG_HTTPSERVER_PORT
```

### 원인
Botpress v12는 새로운 환경 변수 형식을 사용합니다.

### 해결 방법

**잘못된 환경 변수:**
```yaml
environment:
  BP_HOST: 0.0.0.0
  BP_PORT: 3000
```

**올바른 환경 변수:**
```yaml
environment:
  BP_CONFIG_HTTPSERVER_HOST: 0.0.0.0
  BP_CONFIG_HTTPSERVER_PORT: 3000
```

---

## 오류 2: 모듈 로드 실패

### 증상
```
Error while loading module MODULES_ROOT/qna 
[VError, Could not find module at path "MODULES_ROOT/qna"]
```

### 원인
- 모듈이 Docker 이미지에 포함되지 않음
- 설정 파일에서 존재하지 않는 모듈 참조

### 해결 방법

#### 방법 1: 환경 변수로 모듈 비활성화
```yaml
environment:
  # QNA 모듈 비활성화
  BP_MODULE_QNA_ENABLED: "false"
  
  # 또는 필요한 모듈만 활성화
  BP_MODULE_NLU_ENABLED: "true"
  BP_MODULE_BUILTIN_ENABLED: "true"
  BP_MODULE_CHANNEL_WEB_ENABLED: "true"
```

#### 방법 2: 설정 파일 수정
`botpress.config.json` 파일에서 모듈 섹션 수정:
```json
{
  "modules": [
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
    }
  ]
}
```

---

## 오류 3: useCookieStorage 오류

### 증상
```
Unhandled Rejection [TypeError, Cannot read property 'useCookieStorage' of undefined]
```

### 원인
HTTP 서버 설정이 제대로 로드되지 않음

### 해결 방법

Docker Compose 파일에 필수 설정 추가:
```yaml
environment:
  BP_CONFIG_HTTPSERVER_HOST: 0.0.0.0
  BP_CONFIG_HTTPSERVER_PORT: 3000
  BP_CONFIG_HTTPSERVER_BACKLOG: 511
  BP_CONFIG_HTTPSERVER_BODYLIMIT: 100mb
  BP_CONFIG_HTTPSERVER_CORS_ENABLED: "true"
  BP_CONFIG_HTTPSERVER_USECOOKIESTORAGE: "true"
```

---

## 오류 4: AuthService 초기화 실패

### 증상
```
Error starting Botpress [TypeError, Cannot convert undefined or null to object]
at AuthService.initialize
```

### 원인
인증 관련 설정이 누락됨

### 해결 방법

서명 키(appSecret) 설정 추가(고정값):
```yaml
environment:
  BP_CONFIG_APPSECRET: "change-this-appsecret-in-production-please-use-32+chars"
  BP_CONFIG_PRO_ENABLED: "false"
```

---

## 오류 5: 데이터베이스 연결 실패

### 증상
```
Error: connect ECONNREFUSED
Could not connect to database
```

### 원인
- PostgreSQL이 시작되지 않음
- 데이터베이스 URL이 잘못됨
- 네트워크 문제

### 해결 방법

#### 1. PostgreSQL 상태 확인
```bash
docker compose ps postgres
docker compose logs postgres
```

#### 2. 데이터베이스 연결 테스트
```bash
docker exec -it botpress-postgres psql -U botpress -d botpress
```

#### 3. DATABASE_URL 확인
```yaml
environment:
  DATABASE_URL: postgres://botpress:botpress_secure_password_2024@postgres:5432/botpress
```

#### 4. 헬스체크 확인
```yaml
depends_on:
  postgres:
    condition: service_healthy
```

---

## 오류 6: 포트 충돌

### 증상
```
Error starting userland proxy: listen tcp 0.0.0.0:3000: bind: address already in use
```

### 해결 방법

#### Rocky Linux에서 포트 확인
```bash
sudo ss -tulpn | grep :3000
```

#### 프로세스 종료
```bash
# 프로세스 ID 확인
sudo lsof -i :3000

# 프로세스 종료
sudo kill -9 <PID>
```

#### 또는 다른 포트 사용
```yaml
ports:
  - "3001:3000"  # 외부:내부
```

---

## 오류 7: SELinux 차단 (Rocky Linux)

### 증상
```
Permission denied
failed to create shim task
```

### 해결 방법

#### 1. SELinux 로그 확인
```bash
sudo ausearch -m avc -ts recent | grep botpress
```

#### 2. 임시 해결 (테스트용)
```bash
sudo setenforce 0
docker compose restart
```

#### 3. 영구 해결 (권장)
```bash
# Docker 볼륨 컨텍스트 설정
sudo chcon -Rt svirt_sandbox_file_t /opt/botpress

# SELinux boolean 설정
sudo setsebool -P container_manage_cgroup on

# 재시작
docker compose restart
```

---

## 오류 8: 방화벽 차단 (Rocky Linux)

### 증상
웹 브라우저에서 접속 불가

### 해결 방법

```bash
# 방화벽 상태 확인
sudo firewall-cmd --list-all

# 포트 개방
sudo firewall-cmd --permanent --add-port=3000/tcp
sudo firewall-cmd --reload

# 확인
sudo firewall-cmd --list-ports
```

---

## 완전한 Docker Compose 설정 예제

### 수정된 docker-compose.yml
```yaml
version: '3.8'

services:
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

  botpress:
    image: botpress/server:12.26.11
    container_name: botpress-server
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      # 데이터베이스
      DATABASE_URL: postgres://botpress:botpress_secure_password_2024@postgres:5432/botpress
      
      # HTTP 서버 (올바른 형식)
      BP_CONFIG_HTTPSERVER_HOST: 0.0.0.0
      BP_CONFIG_HTTPSERVER_PORT: 3000
      BP_CONFIG_HTTPSERVER_BACKLOG: 511
      BP_CONFIG_HTTPSERVER_BODYLIMIT: 100mb
      BP_CONFIG_HTTPSERVER_CORS_ENABLED: "true"
      
      # 외부 URL
      EXTERNAL_URL: http://192.168.133.132:3000
      
      # 프로덕션 모드
      BP_PRODUCTION: "true"
      
      # 로그
      VERBOSITY_LEVEL: "info"
      
      # 모듈
      BP_MODULE_NLU_DUCKLINGURL: http://duckling:8000
      BP_MODULE_NLU_ENABLED: "true"
      BP_MODULE_BUILTIN_ENABLED: "true"
      BP_MODULE_CHANNEL_WEB_ENABLED: "true"
      
      # 인증
      BP_CONFIG_JWTTOKEN_SECRET: "change-this-secret-in-production"
      BP_CONFIG_JWTTOKEN_DURATION: "6h"
      
      # Pro 기능 비활성화
      BP_CONFIG_PRO_ENABLED: "false"
      
      # 대화 설정
      BP_CONFIG_DIALOG_JANITORINTERVAL: "10s"
      BP_CONFIG_DIALOG_TIMEOUTINTERVAL: "2m"
      
      # 클러스터링
      CLUSTER_ENABLED: "false"
    ports:
      - "3000:3000"
    volumes:
      - botpress_data:/botpress/data
    networks:
      - botpress-network
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:3000/status"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

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
  botpress_data:
```

---

## 문제 해결 프로세스

### 1단계: 로그 확인
```bash
# 전체 로그
docker compose logs

# Botpress만
docker compose logs botpress

# 실시간 로그
docker compose logs -f botpress

# 최근 100줄
docker compose logs --tail=100 botpress
```

### 2단계: 컨테이너 상태 확인
```bash
# 실행 중인 컨테이너
docker compose ps

# 리소스 사용량
docker stats

# 컨테이너 내부 접속
docker exec -it botpress-server sh
```

### 3단계: 네트워크 확인
```bash
# 네트워크 목록
docker network ls

# 네트워크 상세 정보
docker network inspect botpress_botpress-network

# 컨테이너 간 연결 테스트
docker exec botpress-server ping postgres
```

### 4단계: 볼륨 확인
```bash
# 볼륨 목록
docker volume ls

# 볼륨 상세 정보
docker volume inspect botpress_botpress_data

# 볼륨 내용 확인
docker run --rm -v botpress_botpress_data:/data alpine ls -la /data
```

---

## 완전 재설치

문제가 계속되면 완전히 재설치:

```bash
# 1. 모든 컨테이너 중지 및 제거
docker compose down

# 2. 볼륨 제거 (데이터 삭제 주의!)
docker compose down -v

# 3. 이미지 제거
docker rmi botpress/server:12.26.11
docker rmi postgres:13-alpine
docker rmi rasa/duckling:latest

# 4. 네트워크 정리
docker network prune -f

# 5. 이미지 다시 받기
docker compose pull

# 6. 시작
docker compose up -d

# 7. 로그 확인
docker compose logs -f
```

---

## 디버그 모드로 실행

더 자세한 로그가 필요한 경우:

```yaml
environment:
  VERBOSITY_LEVEL: "debug"
  DEBUG: "bp:*"
  NODE_ENV: "development"
```

```bash
# 재시작
docker compose restart botpress

# 로그 확인
docker compose logs -f botpress
```

---

## 유용한 명령어

### 로그 관리
```bash
# 로그 파일 크기 확인
docker inspect botpress-server --format='{{.LogPath}}' | xargs ls -lh

# 로그 정리
docker compose down
docker system prune -a --volumes
```

### 성능 모니터링
```bash
# 실시간 리소스 사용량
docker stats botpress-server

# 컨테이너 프로세스
docker top botpress-server
```

### 데이터베이스 관리
```bash
# PostgreSQL 접속
docker exec -it botpress-postgres psql -U botpress -d botpress

# 테이블 목록
\dt

# 데이터베이스 크기
SELECT pg_size_pretty(pg_database_size('botpress'));

# 백업
docker exec botpress-postgres pg_dump -U botpress botpress > backup.sql

# 복원
cat backup.sql | docker exec -i botpress-postgres psql -U botpress -d botpress
```

---

## 지원 및 추가 리소스

### 공식 문서
- Botpress v12 문서: https://v12.botpress.com/docs
- Docker 문서: https://docs.docker.com/
- PostgreSQL 문서: https://www.postgresql.org/docs/

### 커뮤니티
- Botpress 포럼: https://forum.botpress.com/
- GitHub Issues: https://github.com/botpress/botpress/issues
- Discord: https://discord.gg/botpress

### 관련 문서
- 📚 BOTPRESS_INSTALLATION_GUIDE.md
- 🚀 BOTPRESS_QUICK_START.md
- 🐧 ROCKY_LINUX_SETUP.md

---

**작성일**: 2024-12-22  
**Botpress 버전**: v12.26.11  
**대상 OS**: Rocky Linux 9.6

