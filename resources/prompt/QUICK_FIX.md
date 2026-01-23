# 🚨 Botpress 빠른 수정 가이드

## 현재 발생한 오류 해결 방법

---

## ⚡ 즉시 해결 방법

### 1단계: 컨테이너 중지
```bash
cd /opt/botpress
docker compose down
```

### 2단계: docker-compose.yml 수정
```bash
vi docker-compose.yml
```

**`i` 키를 눌러 편집 모드로 전환 후, `environment` 섹션을 다음과 같이 수정:**

```yaml
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
      VERBOSITY_LEVEL: "info"
      
      # 모듈 설정
      BP_MODULE_NLU_DUCKLINGURL: http://duckling:8000
      BP_MODULE_NLU_ENABLED: "true"
      BP_MODULE_BUILTIN_ENABLED: "true"
      BP_MODULE_CHANNEL_WEB_ENABLED: "true"
      
      # 인증 설정 (필수!)
      BP_CONFIG_JWTTOKEN_SECRET: "change-this-secret-key-in-production-12345"
      BP_CONFIG_JWTTOKEN_DURATION: "6h"
      BP_CONFIG_PRO_ENABLED: "false"
      
      # 대화 설정
      BP_CONFIG_DIALOG_JANITORINTERVAL: "10s"
      BP_CONFIG_DIALOG_TIMEOUTINTERVAL: "2m"
      
      # 클러스터링
      CLUSTER_ENABLED: "false"
```

**`ESC` 키를 누른 후 `:wq` 입력하고 Enter (저장 후 종료)**

### 3단계: 재시작
```bash
docker compose up -d
```

### 4단계: 로그 확인
```bash
docker compose logs -f botpress
```

**성공 메시지 확인:**
```
Botpress Pro must be enabled to use a license key
Botpress is listening at: http://0.0.0.0:3000
Botpress is exposed at: http://192.168.133.132:3000
```

### 5단계: 웹 접속
```
http://192.168.133.132:3000
```

---

## 🔄 대체 방법: 수정된 파일 사용

프로젝트에 이미 수정된 `docker-compose.botpress.yml` 파일이 있습니다.

```bash
cd /opt/botpress

# 기존 파일 백업
mv docker-compose.yml docker-compose.yml.backup

# 수정된 파일 복사 (프로젝트 디렉토리에서)
cp /path/to/aicc-chat/docker-compose.botpress.yml docker-compose.yml

# 재시작
docker compose down
docker compose up -d
docker compose logs -f botpress
```

---

## 📋 주요 변경 사항

### ❌ 잘못된 환경 변수 (이전)
```yaml
BP_HOST: 0.0.0.0          # 잘못됨
BP_PORT: 3000             # 잘못됨
SUPERADMIN_EMAIL: ...     # 작동 안 함
SUPERADMIN_PASSWORD: ...  # 작동 안 함
```

### ✅ 올바른 환경 변수 (현재)
```yaml
BP_CONFIG_HTTPSERVER_HOST: 0.0.0.0     # 올바름
BP_CONFIG_HTTPSERVER_PORT: 3000        # 올바름
BP_CONFIG_JWTTOKEN_SECRET: "..."       # 필수!
BP_CONFIG_PRO_ENABLED: "false"         # 필수!
```

---

## 🔍 오류 확인 방법

### 로그에서 다음 메시지가 나타나면 성공:
```
✓ Loaded 5 modules
✓ Botpress is listening at: http://0.0.0.0:3000
✓ Botpress is exposed at: http://192.168.133.132:3000
```

### 여전히 오류가 발생하면:
```bash
# 전체 로그 확인
docker compose logs botpress

# 데이터베이스 확인
docker compose logs postgres

# 컨테이너 상태 확인
docker compose ps
```

---

## 🔴 포트 충돌 오류 해결

### 오류: Redis 포트 6379 충돌
```
failed to bind host port 0.0.0.0:6379/tcp: address already in use
```

**원인**: 기존 Redis가 이미 실행 중

**해결 방법 1: 기존 Redis 확인 및 중지**
```bash
# 포트 6379 사용 중인 프로세스 확인
sudo ss -tulpn | grep :6379

# Docker 컨테이너 확인
docker ps | grep redis

# 기존 Redis 컨테이너 중지
docker stop <container_name>

# 또는 모든 Redis 컨테이너 중지
docker ps -a | grep redis | awk '{print $1}' | xargs docker stop
```

**해결 방법 2: Botpress용 Redis 제거 (권장)**

Botpress는 Redis 없이도 작동합니다. docker-compose.yml에서 Redis 섹션 제거:

```bash
cd /opt/botpress
vi docker-compose.yml
```

**Redis 섹션 전체를 주석 처리하거나 삭제:**
```yaml
  # Redis (선택사항 - Botpress는 Redis 없이도 작동)
  # redis:
  #   image: redis:7.2-alpine
  #   container_name: botpress-redis
  #   ...
```

**volumes 섹션에서도 redis_data 제거:**
```yaml
volumes:
  postgres_data:
    driver: local
  botpress_data:
    driver: local
  # redis_data:  # 제거 또는 주석 처리
  #   driver: local
```

**재시작:**
```bash
docker compose down
docker compose up -d
docker compose logs -f botpress
```

**해결 방법 3: 다른 포트 사용**

Redis를 유지하되 다른 포트 사용:
```yaml
  redis:
    ports:
      - "6380:6379"  # 외부 포트를 6380으로 변경
```

---

## 🆘 여전히 문제가 있다면

### 완전 재설치
```bash
# 1. 모든 것 중지 및 제거
cd /opt/botpress
docker compose down -v

# 2. 이미지 다시 받기
docker compose pull

# 3. 시작
docker compose up -d

# 4. 로그 확인
docker compose logs -f botpress
```

### 방화벽 확인 (Rocky Linux)
```bash
sudo firewall-cmd --list-all
sudo firewall-cmd --permanent --add-port=3000/tcp
sudo firewall-cmd --reload
```

### SELinux 확인 (Rocky Linux)
```bash
getenforce
sudo setenforce 0  # 임시 비활성화 (테스트용)
docker compose restart
```

---

## 📞 추가 지원

더 자세한 정보는 다음 문서를 참조하세요:

- 🔧 **BOTPRESS_TROUBLESHOOTING.md** - 전체 문제 해결 가이드
- 🐧 **ROCKY_LINUX_SETUP.md** - Rocky Linux 전용 가이드
- 📚 **BOTPRESS_INSTALLATION_GUIDE.md** - 완전 설치 가이드

---

**작성일**: 2024-12-22  
**긴급 수정**: 환경 변수 형식 오류

