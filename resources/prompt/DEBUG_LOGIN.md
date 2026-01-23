# 🔍 Botpress 로그인 문제 상세 디버깅

## 현재 상황 확인

로그인은 성공하지만 메인 화면으로 전환되지 않고 로그인 페이지로 계속 리다이렉트되는 문제

---

## 🚨 즉시 실행: 완전 수정 방법

### 1단계: 컨테이너 완전 재시작

```bash
cd /opt/botpress

# 1. 모든 컨테이너 중지 및 제거
docker compose down

# 2. 볼륨은 유지하고 재시작
docker compose up -d

# 3. 로그 확인 (30초 대기)
sleep 30
docker compose logs botpress | tail -50
```

### 2단계: 브라우저 완전 초기화

**중요! 반드시 실행하세요:**

1. **모든 브라우저 탭 닫기**
2. **브라우저 캐시 완전 삭제:**
   - Chrome/Edge: `chrome://settings/clearBrowserData`
   - 시간 범위: **전체 기간**
   - 항목 체크:
     - ✅ 쿠키 및 기타 사이트 데이터
     - ✅ 캐시된 이미지 및 파일
     - ✅ 호스팅된 앱 데이터
   - **데이터 삭제** 클릭

3. **브라우저 완전 재시작**

4. **시크릿 모드로 테스트:**
   ```
   Ctrl + Shift + N (Chrome/Edge)
   ```

5. **접속:**
   ```
   http://192.168.133.132:3000
   ```

---

## 🔧 Docker Compose 설정 확인

### 현재 설정 확인:

```bash
cd /opt/botpress
cat docker-compose.yml | grep -A 20 "environment:"
```

### 필수 환경 변수 확인:

```yaml
environment:
  # 데이터베이스
  DATABASE_URL: postgres://botpress:botpress_secure_password_2024@postgres:5432/botpress
  
  # HTTP 서버 (필수!)
  BP_CONFIG_HTTPSERVER_HOST: 0.0.0.0
  BP_CONFIG_HTTPSERVER_PORT: 3000
  
  # CORS 설정 (필수!)
  BP_CONFIG_HTTPSERVER_CORS_ENABLED: "true"
  BP_CONFIG_HTTPSERVER_CORS_ORIGIN: "http://192.168.133.132:3000"
  BP_CONFIG_HTTPSERVER_CORS_CREDENTIALS: "true"
  
  # 쿠키 설정 (필수!)
  BP_CONFIG_HTTPSERVER_COOKIESTORAGE: "true"
  
  # External URL (필수!)
  BP_CONFIG_HTTPSERVER_EXTERNALURL: "http://192.168.133.132:3000"
  EXTERNAL_URL: http://192.168.133.132:3000
  
  # JWT 설정 (필수!)
  BP_CONFIG_JWTTOKEN_SECRET: "change-this-secret-key-in-production-12345"
  BP_CONFIG_JWTTOKEN_DURATION: "6h"
  BP_CONFIG_JWTTOKEN_ALLOWREFRESH: "true"
```

### 환경 변수가 없다면 추가:

```bash
cd /opt/botpress
vi docker-compose.yml

# i 키로 편집 모드
# environment 섹션에 위의 변수들 추가
# ESC → :wq → Enter
```

---

## 🌐 브라우저 개발자 도구로 확인

### 1. 개발자 도구 열기 (F12)

### 2. Network 탭 확인

**로그인 시도 후 확인할 요청:**

1. **POST /api/v2/admin/auth/login/default**
   - Status: 200 OK 확인
   - Response에 토큰 있는지 확인

2. **GET /api/v2/admin/user/workspace**
   - Status: 200 OK 확인
   - Authorization 헤더에 Bearer 토큰 있는지 확인
   - Response Headers 확인:
     ```
     Access-Control-Allow-Origin: http://192.168.133.132:3000
     Access-Control-Allow-Credentials: true
     Set-Cookie: ...
     ```

### 3. Console 탭 확인

**오류 메시지 확인:**
- CORS 오류
- 쿠키 관련 경고
- 리다이렉트 루프 오류

### 4. Application 탭 → Cookies 확인

**http://192.168.133.132:3000 쿠키 확인:**
- `bp-session` 또는 세션 쿠키 존재 여부
- 쿠키 속성:
  - HttpOnly: true
  - SameSite: Lax
  - Secure: false (HTTP이므로)

---

## 🔍 로그 상세 확인

```bash
cd /opt/botpress

# 1. 전체 로그
docker compose logs botpress > botpress_full.log
cat botpress_full.log | grep -i "error\|warn\|cors\|cookie\|auth"

# 2. 실시간 로그 (새 터미널)
docker compose logs -f botpress

# 3. 로그인 시도 후 로그 확인
docker compose logs botpress | tail -100
```

**찾아야 할 오류:**
- CORS 관련 오류
- JWT 토큰 오류
- 세션 관련 오류
- 데이터베이스 연결 오류

---

## 🛠️ 완전 수정 스크립트

```bash
#!/bin/bash
# 로그인 문제 완전 수정 스크립트

cd /opt/botpress

echo "1. 컨테이너 중지..."
docker compose down

echo "2. docker-compose.yml 백업..."
cp docker-compose.yml docker-compose.yml.backup

echo "3. 환경 변수 확인..."
if ! grep -q "BP_CONFIG_HTTPSERVER_CORS_CREDENTIALS" docker-compose.yml; then
    echo "❌ CORS credentials 설정이 없습니다!"
    echo "docker-compose.yml을 수정해야 합니다."
    exit 1
fi

if ! grep -q "BP_CONFIG_HTTPSERVER_COOKIESTORAGE" docker-compose.yml; then
    echo "❌ Cookie storage 설정이 없습니다!"
    echo "docker-compose.yml을 수정해야 합니다."
    exit 1
fi

echo "4. 컨테이너 재시작..."
docker compose up -d

echo "5. 초기화 대기 (30초)..."
sleep 30

echo "6. 상태 확인..."
docker compose ps

echo "7. 로그 확인..."
docker compose logs botpress | tail -20

echo ""
echo "✅ 완료!"
echo ""
echo "다음 단계:"
echo "1. 브라우저 캐시 완전 삭제"
echo "2. 브라우저 재시작"
echo "3. 시크릿 모드로 접속: http://192.168.133.132:3000"
```

**실행:**
```bash
chmod +x fix-login.sh
./fix-login.sh
```

---

## 🔄 대체 해결 방법

### 방법 1: 설정 파일로 강제 설정

```bash
cd /opt/botpress

# 설정 파일 생성
cat > botpress.config.json << 'EOF'
{
  "$schema": "../../assets/config-schema.json",
  "version": "12.26.11",
  "appSecret": "my-secret-key-change-in-production",
  "httpServer": {
    "host": "0.0.0.0",
    "port": 3000,
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
      "sameSite": "lax",
      "maxAge": 86400000
    }
  },
  "jwtToken": {
    "secret": "change-this-secret-key-in-production-12345",
    "duration": "6h",
    "allowRefresh": true
  },
  "database": {
    "type": "postgres",
    "url": "postgres://botpress:botpress_secure_password_2024@postgres:5432/botpress"
  },
  "logs": {
    "level": "debug"
  },
  "pro": {
    "enabled": false
  }
}
EOF

# 설정 파일을 볼륨에 복사
docker run --rm -v botpress_botpress_data:/data -v $(pwd):/host alpine \
  cp /host/botpress.config.json /data/global/botpress.config.json

# 재시작
docker compose restart botpress
docker compose logs -f botpress
```

### 방법 2: 포트 변경 테스트

혹시 포트 문제일 수 있으니 다른 포트로 테스트:

```bash
cd /opt/botpress
vi docker-compose.yml

# ports 섹션 수정
ports:
  - "3001:3000"  # 3000 → 3001로 변경

# environment 섹션도 수정
EXTERNAL_URL: http://192.168.133.132:3001
BP_CONFIG_HTTPSERVER_EXTERNALURL: "http://192.168.133.132:3001"
BP_CONFIG_HTTPSERVER_CORS_ORIGIN: "http://192.168.133.132:3001"

# 재시작
docker compose down
docker compose up -d

# 접속
# http://192.168.133.132:3001
```

---

## 📊 체크리스트

### 서버 측 확인:
- [ ] 컨테이너 정상 실행: `docker compose ps`
- [ ] 로그에 오류 없음: `docker compose logs botpress`
- [ ] 환경 변수 설정 완료
- [ ] 포트 3000 개방: `sudo ss -tulpn | grep :3000`
- [ ] 방화벽 허용: `sudo firewall-cmd --list-ports`

### 브라우저 측 확인:
- [ ] 캐시 완전 삭제
- [ ] 쿠키 완전 삭제
- [ ] 브라우저 재시작
- [ ] 시크릿 모드 테스트
- [ ] 개발자 도구에서 CORS 오류 없음
- [ ] 쿠키 저장 확인

### 네트워크 확인:
- [ ] 서버 IP 접근 가능: `ping 192.168.133.132`
- [ ] 포트 접근 가능: `telnet 192.168.133.132 3000`
- [ ] curl 테스트: `curl http://192.168.133.132:3000`

---

## 🆘 여전히 안 되면

### 상세 정보 수집:

```bash
# 1. 환경 변수 전체 출력
docker exec botpress-server env | grep BP_CONFIG > bp_env.txt
cat bp_env.txt

# 2. 설정 파일 확인
docker exec botpress-server cat /botpress/data/global/botpress.config.json

# 3. 로그 전체 저장
docker compose logs botpress > botpress_debug.log

# 4. 네트워크 확인
docker network inspect botpress_botpress-network

# 5. 브라우저 Network 탭 스크린샷
# - 로그인 요청
# - API 응답
# - 헤더 정보
```

### 다음 정보 제공 필요:

1. **브라우저 Console 오류 메시지**
2. **Network 탭의 /api/v2/admin/user/workspace 응답**
3. **Application 탭의 쿠키 목록**
4. **docker compose logs botpress의 최근 100줄**

---

**작성일**: 2024-12-22  
**긴급**: 로그인 문제 상세 디버깅

