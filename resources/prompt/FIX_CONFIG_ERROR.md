# 🚨 Botpress 설정 파일 오류 해결

## 오류: botpress.config.json not found

```
Error reading configuration file "botpress.config.json": 
Modules configuration file "botpress.config.json" not found
```

---

## ⚡ 즉시 해결 (권장 방법)

### 방법 1: 볼륨 권한 및 초기화 (가장 확실)

```bash
# 1. 컨테이너 중지
cd /opt/botpress
docker compose down

# 2. 볼륨 완전 제거 (데이터 초기화)
docker compose down -v

# 3. 볼륨 디렉토리 권한 설정
sudo mkdir -p /var/lib/docker/volumes/botpress_botpress_data/_data
sudo chmod -R 777 /var/lib/docker/volumes/botpress_botpress_data/_data

# 4. 재시작 (초기 설정 파일 자동 생성)
docker compose up -d

# 5. 로그 확인 (30초 대기)
sleep 30
docker compose logs botpress
```

---

### 방법 2: 설정 파일 수동 생성

```bash
# 1. 컨테이너 중지
cd /opt/botpress
docker compose down

# 2. 설정 파일 생성
cat > botpress.config.json << 'EOF'
{
  "$schema": "../../assets/config-schema.json",
  "version": "12.26.11",
  "appSecret": "my-secret-key-change-in-production",
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
      "enabled": false
    },
    {
      "location": "MODULES_ROOT/basic-skills",
      "enabled": false
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
      "enabled": false
    }
  ],
  "pro": {
    "enabled": false
  },
  "dialog": {
    "janitorInterval": "10s",
    "timeoutInterval": "2m"
  }
}
EOF

# 3. docker-compose.yml 수정하여 설정 파일 마운트
vi docker-compose.yml
```

**volumes 섹션에 추가:**
```yaml
    volumes:
      - botpress_data:/botpress/data
      - ./botpress.config.json:/botpress/data/global/botpress.config.json:ro
```

```bash
# 4. 재시작
docker compose up -d

# 5. 로그 확인
docker compose logs -f botpress
```

---

### 방법 3: 환경 변수만으로 실행 (설정 파일 없이)

docker-compose.yml을 수정하여 모든 설정을 환경 변수로 처리:

```bash
cd /opt/botpress
vi docker-compose.yml
```

**environment 섹션에 추가:**
```yaml
    environment:
      # 기존 설정...
      
      # 설정 파일 없이 실행
      BP_CONFIG_MODULES_LOCATION: ""
      BP_CONFIG_DISABLE_GLOBAL_SANDBOX: "true"
      
      # 모듈 명시적 비활성화
      BP_MODULE_ANALYTICS_ENABLED: "false"
      BP_MODULE_BASIC_SKILLS_ENABLED: "false"
      BP_MODULE_QNA_ENABLED: "false"
```

---

## 🔧 상세 해결 방법

### 1단계: 현재 상태 확인

```bash
cd /opt/botpress

# 컨테이너 상태
docker compose ps

# 볼륨 확인
docker volume ls | grep botpress

# 볼륨 상세 정보
docker volume inspect botpress_botpress_data
```

### 2단계: 볼륨 내용 확인

```bash
# 볼륨 내부 확인
docker run --rm -v botpress_botpress_data:/data alpine ls -la /data

# global 디렉토리 확인
docker run --rm -v botpress_botpress_data:/data alpine ls -la /data/global
```

**예상 출력:**
```
drwxr-xr-x    2 root     root          4096 Dec 22 10:00 global
-rw-r--r--    1 root     root          2048 Dec 22 10:00 botpress.config.json
```

### 3단계: 설정 파일 생성 (볼륨 내부)

```bash
# 컨테이너 중지
docker compose down

# 설정 파일을 볼륨에 직접 생성
docker run --rm -v botpress_botpress_data:/data alpine sh -c '
mkdir -p /data/global
cat > /data/global/botpress.config.json << "EOFCONFIG"
{
  "$schema": "../../assets/config-schema.json",
  "version": "12.26.11",
  "appSecret": "my-secret-key-change-in-production",
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
    "level": "info"
  },
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
  ],
  "pro": {
    "enabled": false
  }
}
EOFCONFIG
chmod 644 /data/global/botpress.config.json
'

# 생성 확인
docker run --rm -v botpress_botpress_data:/data alpine cat /data/global/botpress.config.json

# 재시작
docker compose up -d
docker compose logs -f botpress
```

---

## 🔄 완전 재설치 (가장 확실한 방법)

```bash
cd /opt/botpress

# 1. 모든 것 중지 및 제거
docker compose down -v

# 2. 이미지 제거
docker rmi botpress/server:12.26.11

# 3. 볼륨 수동 제거
docker volume rm botpress_botpress_data botpress_postgres_data

# 4. 이미지 다시 받기
docker compose pull

# 5. 시작 (초기 설정 자동 생성)
docker compose up -d

# 6. 초기화 대기 (60초)
echo "초기화 중... 60초 대기"
sleep 60

# 7. 로그 확인
docker compose logs botpress

# 8. 상태 확인
docker compose ps
```

---

## 🐛 디버깅

### 컨테이너 내부 확인

```bash
# 컨테이너 내부 접속
docker exec -it botpress-server sh

# 내부에서 확인
ls -la /botpress/data/
ls -la /botpress/data/global/
cat /botpress/data/global/botpress.config.json

# 권한 확인
ls -la /botpress/data/global/botpress.config.json

# 종료
exit
```

### 볼륨 마운트 확인

```bash
# 컨테이너의 마운트 정보
docker inspect botpress-server | grep -A 10 Mounts
```

---

## 📝 수정된 docker-compose.yml (완전판)

```yaml
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
    user: root
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      DATABASE_URL: postgres://botpress:botpress_secure_password_2024@postgres:5432/botpress
      BP_CONFIG_HTTPSERVER_HOST: 0.0.0.0
      BP_CONFIG_HTTPSERVER_PORT: 3000
      BP_CONFIG_HTTPSERVER_BACKLOG: 511
      BP_CONFIG_HTTPSERVER_BODYLIMIT: 100mb
      BP_CONFIG_HTTPSERVER_CORS_ENABLED: "true"
      EXTERNAL_URL: http://192.168.133.132:3000
      BP_PRODUCTION: "true"
      VERBOSITY_LEVEL: "info"
      BP_MODULE_NLU_DUCKLINGURL: http://duckling:8000
      BP_MODULE_NLU_ENABLED: "true"
      BP_MODULE_BUILTIN_ENABLED: "true"
      BP_MODULE_CHANNEL_WEB_ENABLED: "true"
      BP_MODULE_ANALYTICS_ENABLED: "false"
      BP_MODULE_QNA_ENABLED: "false"
      BP_CONFIG_JWTTOKEN_SECRET: "change-this-secret-key-in-production-12345"
      BP_CONFIG_JWTTOKEN_DURATION: "6h"
      BP_CONFIG_PRO_ENABLED: "false"
      BP_CONFIG_DIALOG_JANITORINTERVAL: "10s"
      BP_CONFIG_DIALOG_TIMEOUTINTERVAL: "2m"
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
      start_period: 90s

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

## ✅ 성공 확인

### 로그에서 확인할 메시지:
```
✓ Botpress is listening at: http://0.0.0.0:3000
✓ Botpress is exposed at: http://192.168.133.132:3000
✓ Loaded 3 modules
```

### 웹 접속:
```
http://192.168.133.132:3000
```

---

## 🆘 여전히 문제가 있다면

### SELinux 확인 (Rocky Linux)
```bash
# SELinux 상태
getenforce

# 임시 비활성화
sudo setenforce 0

# 재시작
docker compose restart botpress
```

### 볼륨 권한 문제
```bash
# 볼륨 위치 확인
docker volume inspect botpress_botpress_data | grep Mountpoint

# 권한 변경
sudo chmod -R 777 /var/lib/docker/volumes/botpress_botpress_data/_data
```

---

## 📞 추가 지원

- 🔧 **BOTPRESS_TROUBLESHOOTING.md** - 전체 문제 해결
- 🚨 **FIX_PORT_CONFLICT.md** - 포트 충돌 해결
- 🐧 **ROCKY_LINUX_SETUP.md** - Rocky Linux 가이드

---

**작성일**: 2024-12-22  
**긴급 수정**: 설정 파일 오류

