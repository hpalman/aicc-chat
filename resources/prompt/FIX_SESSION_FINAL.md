# 🚨 Botpress 세션 쿠키 문제 최종 해결

## 문제 상황
- JWT 토큰은 localStorage에 정상 저장됨
- API 응답은 200 OK
- CORS 설정 정상
- **하지만 세션 쿠키가 생성되지 않음**
- 로그인 후 메인 화면으로 전환 안 됨

---

## 🔍 근본 원인

Botpress v12는 **JWT 토큰 기반 인증**을 사용하며, 세션 쿠키는 선택사항입니다.
문제는 **프론트엔드가 localStorage의 토큰을 제대로 읽지 못하는 것**입니다.

---

## ⚡ 최종 해결 방법

### 방법 1: 설정 파일로 완전 제어 (가장 확실)

```bash
cd /opt/botpress
docker compose down

# 설정 파일 생성
cat > botpress.config.json << 'EOFCONFIG'
{
  "$schema": "../../assets/config-schema.json",
  "version": "12.26.11",
  "appSecret": "my-secret-key-change-in-production-abc123",
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
      "httpOnly": false,
      "secure": false,
      "sameSite": "lax",
      "maxAge": 86400000
    }
  },
  "jwtToken": {
    "secret": "change-this-jwt-secret-in-production-xyz789",
    "duration": "6h",
    "allowRefresh": true
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
  "superAdmins": [
    {
      "email": "noah@aicess.ai",
      "strategy": "default"
    }
  ]
}
EOFCONFIG

# 설정 파일을 볼륨에 복사
docker run --rm -v botpress_botpress_data:/data -v $(pwd):/host alpine sh -c '
  mkdir -p /data/global
  cp /host/botpress.config.json /data/global/botpress.config.json
  chmod 644 /data/global/botpress.config.json
'

# 확인
docker run --rm -v botpress_botpress_data:/data alpine cat /data/global/botpress.config.json

# 재시작
docker compose up -d
docker compose logs -f botpress
```

---

### 방법 2: BP_PRODUCTION 비활성화 (개발 모드)

프로덕션 모드가 문제일 수 있습니다.

```bash
cd /opt/botpress
vi docker-compose.yml
```

**environment 섹션 수정:**
```yaml
      # 프로덕션 모드 비활성화 (테스트)
      BP_PRODUCTION: "false"
      
      # 개발 모드 활성화
      NODE_ENV: "development"
      
      # 디버그 로그
      VERBOSITY_LEVEL: "debug"
      DEBUG: "bp:*"
```

```bash
docker compose down
docker compose up -d
docker compose logs -f botpress
```

---

### 방법 3: HttpOnly 비활성화 (임시 해결)

HttpOnly 쿠키가 JavaScript에서 접근 불가능할 수 있습니다.

```bash
cd /opt/botpress
vi docker-compose.yml
```

**environment 섹션에 추가:**
```yaml
      # HttpOnly 비활성화 (테스트용)
      BP_CONFIG_HTTPSERVER_COOKIEOPTIONS_HTTPONLY: "false"
```

---

### 방법 4: 완전 재설치 (클린 상태)

```bash
cd /opt/botpress

# 1. 완전 중지 및 제거
docker compose down -v

# 2. 이미지 제거
docker rmi botpress/server:12.26.11

# 3. 볼륨 수동 제거
docker volume rm botpress_botpress_data botpress_postgres_data

# 4. docker-compose.yml 확인
cat docker-compose.yml | grep -A 5 "BP_PRODUCTION"

# 5. BP_PRODUCTION을 false로 변경
vi docker-compose.yml
# BP_PRODUCTION: "false"

# 6. 재시작
docker compose pull
docker compose up -d

# 7. 초기화 대기
sleep 60

# 8. 로그 확인
docker compose logs botpress | tail -50

# 9. 새 계정으로 가입
# http://192.168.133.132:3000
```

---

## 🔧 대체 해결책: 토큰 기반 인증 사용

세션 쿠키 없이 JWT 토큰만으로 작동하도록 설정:

```bash
cd /opt/botpress
vi docker-compose.yml
```

**environment 섹션:**
```yaml
    environment:
      DATABASE_URL: postgres://botpress:botpress_secure_password_2024@postgres:5432/botpress
      
      # HTTP 서버
      BP_CONFIG_HTTPSERVER_HOST: 0.0.0.0
      BP_CONFIG_HTTPSERVER_PORT: 3000
      BP_CONFIG_HTTPSERVER_EXTERNALURL: "http://192.168.133.132:3000"
      
      # CORS (중요!)
      BP_CONFIG_HTTPSERVER_CORS_ENABLED: "true"
      BP_CONFIG_HTTPSERVER_CORS_ORIGIN: "http://192.168.133.132:3000"
      BP_CONFIG_HTTPSERVER_CORS_CREDENTIALS: "true"
      
      # 세션 비활성화 (토큰만 사용)
      BP_CONFIG_HTTPSERVER_SESSION_ENABLED: "false"
      
      # 쿠키 저장소 비활성화
      BP_CONFIG_HTTPSERVER_COOKIESTORAGE: "false"
      
      # JWT 토큰 설정
      BP_CONFIG_JWTTOKEN_SECRET: "my-super-secret-jwt-key-change-in-production"
      BP_CONFIG_JWTTOKEN_DURATION: "24h"
      BP_CONFIG_JWTTOKEN_ALLOWREFRESH: "true"
      
      # 개발 모드
      BP_PRODUCTION: "false"
      NODE_ENV: "development"
      VERBOSITY_LEVEL: "debug"
      
      EXTERNAL_URL: http://192.168.133.132:3000
      BP_CONFIG_PRO_ENABLED: "false"
      
      # 모듈
      BP_MODULE_NLU_DUCKLINGURL: http://duckling:8000
      BP_MODULE_NLU_ENABLED: "true"
      BP_MODULE_BUILTIN_ENABLED: "true"
      BP_MODULE_CHANNEL_WEB_ENABLED: "true"
```

---

## 🌐 브라우저 로컬 스토리지 확인

F12 → Application → Local Storage → `http://192.168.133.132:3000`

**확인할 항목:**
- `bp/token` - JWT 토큰 (있음 ✓)
- `bp/workspace` - 워크스페이스 (있음 ✓)

**문제:**
프론트엔드가 이 토큰을 읽고 있지만 리다이렉트가 안 됨.

---

## 🔍 디버그 로그 확인

```bash
cd /opt/botpress

# 디버그 모드로 재시작
docker compose down
vi docker-compose.yml

# VERBOSITY_LEVEL: "debug" 설정
# DEBUG: "bp:*" 추가

docker compose up -d
docker compose logs -f botpress | grep -i "auth\|session\|cookie\|token"
```

**로그인 시도 후 확인:**
- 토큰 발급 로그
- 세션 생성 로그
- 쿠키 설정 로그

---

## 🆘 최후의 수단: Nginx 리버스 프록시

Botpress 앞에 Nginx를 두고 세션 관리:

```bash
# Nginx 설치
sudo dnf install -y nginx

# 설정 파일
sudo vi /etc/nginx/conf.d/botpress.conf
```

```nginx
upstream botpress {
    server 127.0.0.1:3000;
}

server {
    listen 80;
    server_name 192.168.133.132;

    location / {
        proxy_pass http://botpress;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
        
        # 쿠키 설정
        proxy_cookie_path / "/; SameSite=Lax";
    }
}
```

```bash
# Nginx 시작
sudo systemctl start nginx
sudo systemctl enable nginx

# 방화벽 (80 포트)
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --reload

# docker-compose.yml 포트 변경
# ports:
#   - "127.0.0.1:3000:3000"  # localhost만 접근
```

---

## 📊 체크리스트

### 시도한 것:
- [x] CORS 설정
- [x] 쿠키 설정
- [x] 세션 설정
- [x] External URL 설정
- [x] 브라우저 캐시 삭제

### 시도할 것:
- [ ] BP_PRODUCTION: "false" 설정
- [ ] 설정 파일로 완전 제어
- [ ] HttpOnly: false 테스트
- [ ] 완전 재설치
- [ ] 디버그 로그 확인
- [ ] Nginx 리버스 프록시

---

## 🎯 권장 순서

### 1단계: 개발 모드로 전환
```bash
cd /opt/botpress
vi docker-compose.yml
# BP_PRODUCTION: "false"
# VERBOSITY_LEVEL: "debug"
docker compose down
docker compose up -d
```

### 2단계: 브라우저 완전 초기화
- 모든 탭 닫기
- 캐시 완전 삭제
- 브라우저 재시작
- 시크릿 모드 테스트

### 3단계: 새 계정으로 테스트
- 기존 계정 문제일 수 있음
- 새 이메일로 가입
- 로그인 테스트

### 4단계: 여전히 안 되면
- 설정 파일 방법 (방법 1)
- 또는 완전 재설치 (방법 4)

---

## 💡 추가 확인 사항

### 브라우저 Console에서 실행:

```javascript
// localStorage 확인
console.log(localStorage.getItem('bp/token'));
console.log(localStorage.getItem('bp/workspace'));

// 토큰 파싱
const token = JSON.parse(localStorage.getItem('bp/token'));
console.log('Token:', token.token);
console.log('Expires:', new Date(token.expiresAt * 1000));

// 수동 리다이렉트 테스트
window.location.href = 'http://192.168.133.132:3000/admin/workspace/default';
```

마지막 명령어로 수동 리다이렉트가 되는지 확인하세요!

---

**작성일**: 2024-12-22  
**최종 해결**: 세션 쿠키 문제

