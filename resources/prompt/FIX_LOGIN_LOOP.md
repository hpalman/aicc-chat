# 🚨 Botpress 로그인 반복 문제 해결

## 증상

- 사용자 계정 생성 후 로그인 성공
- JWT 토큰은 정상적으로 발급됨
- 하지만 메인 화면으로 전환되지 않고 로그인 페이지로 계속 리다이렉트
- `/api/v2/admin/user/workspace` 호출은 성공하지만 화면 전환 안 됨

---

## 🔍 원인 분석

### 1. CORS 설정 부족
- `CORS_ENABLED: true`만으로는 부족
- `credentials: true` 설정 필요
- Origin 명시 필요

### 2. 쿠키 설정 누락
- 세션 쿠키가 제대로 저장되지 않음
- `httpServer.useCookieStorage` 설정 필요 (환경변수: `BP_CONFIG_HTTPSERVER_USECOOKIESTORAGE`)

### 3. External URL 불일치
- 환경 변수와 실제 접속 URL이 다름

---

## ⚡ 즉시 해결 방법

### 0. 브라우저 캐시/서비스워커 정리 (필수)

Botpress v12는 오래된 프론트 번들이 **브라우저 캐시/서비스워커에 남아** 백엔드와 버전이 꼬이면,
콘솔 에러 없이도 로그인 후 라우팅이 멈추는 경우가 있습니다.

1. `F12` → **Application** 탭
2. **Service Workers** → 등록되어 있으면 **Unregister**
3. **Storage** → **Clear site data**
4. 시크릿 창에서 다시 로그인 테스트

### 방법 1: Docker Compose 환경 변수 수정 (권장)

```bash
cd /opt/botpress
docker compose down
vi docker-compose.yml
```

**environment 섹션에 다음 추가:**

```yaml
    environment:
      # 기존 설정...
      
      # HTTP 서버 설정 (수정)
      BP_CONFIG_HTTPSERVER_HOST: 0.0.0.0
      BP_CONFIG_HTTPSERVER_PORT: 3000
      BP_CONFIG_HTTPSERVER_BACKLOG: 511
      BP_CONFIG_HTTPSERVER_BODYLIMIT: 100mb
      
      # CORS 설정 (중요!)
      BP_CONFIG_HTTPSERVER_CORS_ENABLED: "true"
      BP_CONFIG_HTTPSERVER_CORS_ORIGIN: "http://192.168.133.132:3000"
      BP_CONFIG_HTTPSERVER_CORS_CREDENTIALS: "true"
      
      # 쿠키 설정 (필수!)
      BP_CONFIG_HTTPSERVER_USECOOKIESTORAGE: "true"
      
      # 외부 URL (일치시키기)
      BP_CONFIG_HTTPSERVER_EXTERNALURL: "http://192.168.133.132:3000"
      EXTERNAL_URL: http://192.168.133.132:3000

      # 인증/서명 키 (필수, 고정값)
      # 로그에 'JWT Secret isn't defined. Generating a random key...'가 뜨면 이 값이 적용되지 않은 것
      BP_CONFIG_APPSECRET: "change-this-appsecret-in-production-please-use-32+chars"

### 방법 1-2: 설정 파일로 강제 적용 (권장, 가장 확실)

서버 `/opt/botpress`에 `botpress.config.json`을 두고 마운트합니다:

```bash
cd /opt/botpress
vi botpress.config.json
```

`docker-compose.yml`의 `botpress` 서비스에 추가:

```yaml
volumes:
  - botpress_data:/botpress/data
  - ./botpress.config.json:/botpress/data/global/botpress.config.json:ro
```
```

**재시작:**
```bash
docker compose up -d
docker compose logs -f botpress
```

---

### 방법 2: 설정 파일 수정

```bash
cd /opt/botpress

# 설정 파일 생성/수정
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
    "externalUrl": "http://192.168.133.132:3000",
    "cors": {
      "enabled": true,
      "origin": "http://192.168.133.132:3000",
      "credentials": true
    },
    "session": {
      "enabled": true
    },
    "cookieOptions": {
      "httpOnly": true,
      "secure": false,
      "sameSite": "lax"
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
  },
  "jwtToken": {
    "secret": "change-this-secret-key-in-production-12345",
    "duration": "6h",
    "allowRefresh": true
  }
}
EOF

# docker-compose.yml에 볼륨 마운트 추가
vi docker-compose.yml
```

**volumes 섹션 수정:**
```yaml
    volumes:
      - botpress_data:/botpress/data
      - ./botpress.config.json:/botpress/data/global/botpress.config.json:ro
```

**재시작:**
```bash
docker compose down
docker compose up -d
```

---

## 🔧 상세 해결 단계

### 1단계: 현재 설정 확인

```bash
cd /opt/botpress

# 컨테이너 로그 확인
docker compose logs botpress | grep -i cors
docker compose logs botpress | grep -i cookie

# 환경 변수 확인
docker exec botpress-server env | grep BP_CONFIG
```

### 2단계: CORS 설정 추가

**필수 환경 변수:**
```yaml
BP_CONFIG_HTTPSERVER_CORS_ENABLED: "true"
BP_CONFIG_HTTPSERVER_CORS_ORIGIN: "http://192.168.133.132:3000"
BP_CONFIG_HTTPSERVER_CORS_CREDENTIALS: "true"
```

### 3단계: 쿠키 저장소 활성화

```yaml
BP_CONFIG_HTTPSERVER_COOKIESTORAGE: "true"
```

### 4단계: External URL 일치

```yaml
BP_CONFIG_HTTPSERVER_EXTERNALURL: "http://192.168.133.132:3000"
EXTERNAL_URL: http://192.168.133.132:3000
```

### 5단계: 재시작 및 테스트

```bash
docker compose down
docker compose up -d

# 로그 확인
docker compose logs -f botpress

# 브라우저 캐시 및 쿠키 삭제 후 재접속
```

---

## 🌐 브라우저 설정

### 1. 브라우저 캐시 및 쿠키 삭제

**Chrome/Edge:**
1. `F12` 개발자 도구 열기
2. `Application` 탭
3. `Storage` → `Clear site data`
4. 모든 항목 체크
5. `Clear data` 클릭

**또는 시크릿 모드로 테스트:**
- `Ctrl + Shift + N` (Chrome/Edge)
- `Ctrl + Shift + P` (Firefox)

### 2. 쿠키 확인

**개발자 도구 → Application → Cookies:**
- `http://192.168.133.132:3000` 확인
- 다음 쿠키가 있어야 함:
  - `bp-session` 또는 유사한 세션 쿠키
  - `i18next` (언어 설정)

### 3. 네트워크 요청 확인

**개발자 도구 → Network:**
1. 로그인 후 `/api/v2/admin/user/workspace` 요청 확인
2. Response Headers에서 확인:
   ```
   Access-Control-Allow-Origin: http://192.168.133.132:3000
   Access-Control-Allow-Credentials: true
   Set-Cookie: ...
   ```

---

## 📋 완전한 docker-compose.yml

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
      # 데이터베이스
      DATABASE_URL: postgres://botpress:botpress_secure_password_2024@postgres:5432/botpress
      
      # HTTP 서버
      BP_CONFIG_HTTPSERVER_HOST: 0.0.0.0
      BP_CONFIG_HTTPSERVER_PORT: 3000
      BP_CONFIG_HTTPSERVER_BACKLOG: 511
      BP_CONFIG_HTTPSERVER_BODYLIMIT: 100mb
      
      # CORS 설정 (로그인 문제 해결)
      BP_CONFIG_HTTPSERVER_CORS_ENABLED: "true"
      BP_CONFIG_HTTPSERVER_CORS_ORIGIN: "http://192.168.133.132:3000"
      BP_CONFIG_HTTPSERVER_CORS_CREDENTIALS: "true"
      
      # 쿠키 설정 (필수!)
      BP_CONFIG_HTTPSERVER_COOKIESTORAGE: "true"
      
      # 외부 URL
      BP_CONFIG_HTTPSERVER_EXTERNALURL: "http://192.168.133.132:3000"
      EXTERNAL_URL: http://192.168.133.132:3000
      
      # 프로덕션
      BP_PRODUCTION: "true"
      VERBOSITY_LEVEL: "info"
      
      # 모듈
      BP_MODULE_NLU_DUCKLINGURL: http://duckling:8000
      BP_MODULE_NLU_ENABLED: "true"
      BP_MODULE_BUILTIN_ENABLED: "true"
      BP_MODULE_CHANNEL_WEB_ENABLED: "true"
      BP_MODULE_ANALYTICS_ENABLED: "false"
      BP_MODULE_QNA_ENABLED: "false"
      BP_MODULE_BASIC_SKILLS_ENABLED: "false"
      
      # 인증
      BP_CONFIG_JWTTOKEN_SECRET: "change-this-secret-key-in-production-12345"
      BP_CONFIG_JWTTOKEN_DURATION: "6h"
      BP_CONFIG_JWTTOKEN_ALLOWREFRESH: "true"
      BP_CONFIG_PRO_ENABLED: "false"
      
      # 대화
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

## 🔍 디버깅

### 로그에서 확인할 내용

```bash
# CORS 관련 로그
docker compose logs botpress | grep -i cors

# 인증 관련 로그
docker compose logs botpress | grep -i auth

# 세션 관련 로그
docker compose logs botpress | grep -i session
```

### 브라우저 콘솔 확인

**개발자 도구 → Console:**
- CORS 오류가 있는지 확인
- 쿠키 관련 경고 확인

**예상 오류:**
```
Access to XMLHttpRequest blocked by CORS policy
Cookie "..." will be soon rejected because it has the "SameSite" attribute set to "None"
```

### API 응답 확인

```bash
# 로그인 API 테스트
curl -X POST http://192.168.133.132:3000/api/v2/admin/auth/login/default \
  -H "Content-Type: application/json" \
  -d '{"email":"noah@aicess.ai","password":"yourpassword"}' \
  -v

# 워크스페이스 API 테스트 (토큰 필요)
curl http://192.168.133.132:3000/api/v2/admin/user/workspace \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -v
```

---

## ✅ 성공 확인

### 1. 로그인 성공 후
- 메인 대시보드로 자동 리다이렉트
- URL: `http://192.168.133.132:3000/admin/workspace/default`

### 2. 브라우저 쿠키 확인
- 세션 쿠키가 저장되어 있음
- 쿠키 속성:
  - `HttpOnly: true`
  - `SameSite: Lax`
  - `Path: /`

### 3. API 응답 헤더
```
Access-Control-Allow-Origin: http://192.168.133.132:3000
Access-Control-Allow-Credentials: true
Set-Cookie: bp-session=...; Path=/; HttpOnly; SameSite=Lax
```

---

## 🆘 여전히 문제가 있다면

### 완전 재시작

```bash
cd /opt/botpress

# 1. 모든 것 중지
docker compose down

# 2. 환경 변수 확인
cat docker-compose.yml | grep -A 5 CORS

# 3. 재시작
docker compose up -d

# 4. 로그 확인
docker compose logs -f botpress

# 5. 브라우저 캐시 완전 삭제
# Chrome: chrome://settings/clearBrowserData
```

### 데이터베이스 확인

```bash
# 사용자 확인
docker exec -it botpress-postgres psql -U botpress -d botpress

# SQL 실행
SELECT * FROM srv_users;
SELECT * FROM srv_workspace_users;

# 종료
\q
```

---

## 📞 추가 지원

- 🔧 **BOTPRESS_TROUBLESHOOTING.md** - 전체 문제 해결
- 🚨 **FIX_CONFIG_ERROR.md** - 설정 파일 오류
- 🐧 **ROCKY_LINUX_SETUP.md** - Rocky Linux 가이드

---

**작성일**: 2024-12-22  
**긴급 수정**: 로그인 반복 문제

