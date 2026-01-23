# Botpress 설치 파일 요약

## 📦 생성된 파일 목록

이 문서는 Botpress v12 설치를 위해 생성된 모든 파일과 그 용도를 설명합니다.

---

## 1. 문서 파일

### 📚 BOTPRESS_INSTALLATION_GUIDE.md
**용도**: Botpress v12 완전 설치 가이드 (상세판)

**내용**:
- 사전 요구사항 및 시스템 준비
- Docker 및 Docker Compose 설치 방법
- 단계별 설치 절차
- 워크플로우 생성 가이드
- NLU (자연어 이해) 설정
- AICC Chat 시스템과의 통합 방법
- 문제 해결 가이드
- 백업 및 복구 절차
- 보안 권장사항

**대상 독자**: 처음 Botpress를 설치하는 개발자 또는 시스템 관리자

**파일 크기**: ~30KB (약 800줄)

---

### 🚀 BOTPRESS_QUICK_START.md
**용도**: Botpress v12 빠른 시작 가이드 (요약판)

**내용**:
- 5분 안에 시작하기
- 필수 명령어만 포함
- 자동/수동 설치 방법
- 기본 사용법
- 문제 해결 빠른 참조
- 첫 번째 봇 만들기

**대상 독자**: 빠르게 시작하고 싶은 개발자

**파일 크기**: ~8KB (약 250줄)

---

### 📋 BOTPRESS_FILES_SUMMARY.md
**용도**: 이 문서 - 생성된 파일 요약

**내용**:
- 모든 생성 파일 목록
- 각 파일의 용도 설명
- 사용 시나리오별 가이드

---

## 2. 설정 파일

### 🐳 docker-compose.botpress.yml
**용도**: Botpress 전용 Docker Compose 설정

**포함된 서비스**:
1. **PostgreSQL 13** - Botpress 데이터베이스
   - 포트: 5432
   - 데이터베이스: botpress
   - 사용자: botpress
   - 볼륨: postgres_data

2. **Botpress Server v12.26.11** - 메인 서버
   - 포트: 3000
   - 외부 URL: http://192.168.133.132:3000
   - 볼륨: botpress_data
   - 리소스 제한: CPU 2코어, 메모리 2GB

3. **Duckling** - 날짜/시간 엔티티 추출
   - 포트: 8000
   - Rasa 공식 이미지 사용

4. **Redis** (선택사항) - 세션 관리
   - 포트: 6379
   - 볼륨: redis_data

**네트워크**: botpress-network (172.25.0.0/16)

**사용 방법**:
```bash
docker-compose -f docker-compose.botpress.yml up -d
```

---

## 3. 설치 스크립트

### 🐧 setup-botpress.sh
**용도**: Linux/Mac용 자동 설치 스크립트

**기능**:
- ✅ 사전 요구사항 자동 확인
- ✅ Docker/Docker Compose 자동 설치 (선택)
- ✅ 작업 디렉토리 자동 생성 (/opt/botpress)
- ✅ 방화벽 자동 설정
- ✅ Botpress 자동 시작
- ✅ 상태 확인 및 헬스체크
- ✅ 색상 코딩된 로그 출력
- ✅ 오류 처리 및 롤백

**실행 방법**:
```bash
chmod +x setup-botpress.sh
./setup-botpress.sh
```

**실행 환경**:
- Ubuntu 20.04+
- Debian 10+
- CentOS 8+ (일부 수정 필요)
- macOS (Homebrew 필요)

**실행 시간**: 약 5-10분 (Docker 설치 포함 시 15-20분)

---

### 💻 setup-botpress.ps1
**용도**: Windows PowerShell용 설치 스크립트

**기능**:
- 📡 원격 서버 (192.168.133.132) 자동 설치
- 📄 수동 설치 가이드 출력
- 💻 로컬 Windows 환경 설치
- 🔐 SSH 연결 테스트
- 📦 파일 자동 전송 (SCP)
- 🎨 색상 코딩된 출력
- 📋 대화형 메뉴 인터페이스

**실행 방법**:
```powershell
# PowerShell 관리자 권한으로 실행
.\setup-botpress.ps1
```

**메뉴 옵션**:
1. 원격 서버 자동 설치 (SSH 사용)
2. 수동 설치 가이드 보기
3. 로컬 Windows 환경에 설치
4. 종료

**요구사항**:
- PowerShell 5.1+
- SSH 클라이언트 (Windows 10 1809+ 내장)
- Docker Desktop (로컬 설치 시)

---

## 4. 업데이트된 파일

### 📖 README.md
**변경 사항**: Botpress 통합 정보 추가

**추가된 섹션**:
- Botpress 통합 개요
- 빠른 시작 가이드 링크
- Botpress 접속 정보
- 통합 설정 예제
- 프로젝트 구조 업데이트

---

## 사용 시나리오별 가이드

### 시나리오 1: 처음 설치하는 경우
**추천 순서**:
1. 📚 `BOTPRESS_INSTALLATION_GUIDE.md` 읽기 (전체 이해)
2. 🐳 `docker-compose.botpress.yml` 확인
3. 🐧 `setup-botpress.sh` 또는 💻 `setup-botpress.ps1` 실행
4. 🚀 `BOTPRESS_QUICK_START.md` 참조하여 첫 봇 생성

---

### 시나리오 2: 빠르게 시작하고 싶은 경우
**추천 순서**:
1. 🚀 `BOTPRESS_QUICK_START.md` 읽기
2. 🐧 `setup-botpress.sh` 실행 (자동 설치)
3. 웹 브라우저로 접속하여 봇 생성

---

### 시나리오 3: 수동으로 설치하고 싶은 경우
**추천 순서**:
1. 📚 `BOTPRESS_INSTALLATION_GUIDE.md`의 "수동 설치" 섹션 참조
2. 🐳 `docker-compose.botpress.yml` 복사
3. 명령어 직접 실행

---

### 시나리오 4: Windows에서 원격 서버에 설치
**추천 순서**:
1. 💻 `setup-botpress.ps1` 실행
2. 메뉴에서 "1. 원격 서버 자동 설치" 선택
3. SSH 사용자명 입력
4. 자동 설치 진행

---

### 시나리오 5: 문제 해결이 필요한 경우
**추천 순서**:
1. 🚀 `BOTPRESS_QUICK_START.md`의 "문제 해결" 섹션 확인
2. 📚 `BOTPRESS_INSTALLATION_GUIDE.md`의 "문제 해결" 섹션 참조
3. 로그 확인: `docker-compose logs -f botpress`

---

## 파일 위치 및 구조

```
aicc-chat/
├── BOTPRESS_INSTALLATION_GUIDE.md    # 상세 설치 가이드
├── BOTPRESS_QUICK_START.md           # 빠른 시작 가이드
├── BOTPRESS_FILES_SUMMARY.md         # 이 파일
├── docker-compose.botpress.yml       # Docker Compose 설정
├── setup-botpress.sh                 # Linux/Mac 설치 스크립트
├── setup-botpress.ps1                # Windows 설치 스크립트
├── README.md                         # 프로젝트 메인 README (업데이트됨)
└── docker-compose.yml                # 기존 인프라 설정
```

---

## 설치 후 확인 사항

### ✅ 체크리스트

#### 1. 서비스 상태 확인
```bash
cd /opt/botpress
docker-compose ps
```

예상 출력:
```
NAME                  STATUS              PORTS
botpress-postgres     running (healthy)   0.0.0.0:5432->5432/tcp
botpress-server       running (healthy)   0.0.0.0:3000->3000/tcp
botpress-duckling     running             0.0.0.0:8000->8000/tcp
```

#### 2. 웹 접속 확인
- URL: http://192.168.133.132:3000
- 상태: 로그인 페이지가 표시되어야 함

#### 3. 로그인 테스트
- 이메일: admin@botpress.local
- 비밀번호: Admin@2024!
- 결과: 대시보드 접속 성공

#### 4. 데이터베이스 연결 확인
```bash
docker exec -it botpress-postgres psql -U botpress -d botpress -c "SELECT version();"
```

#### 5. API 엔드포인트 확인
```bash
curl http://192.168.133.132:3000/status
```

예상 응답: `{"status":"ok"}`

---

## 주요 설정 값

### 데이터베이스
- **호스트**: postgres (Docker 네트워크 내부)
- **포트**: 5432
- **데이터베이스**: botpress
- **사용자**: botpress
- **비밀번호**: botpress_secure_password_2024 ⚠️ 변경 권장

### Botpress 서버
- **호스트**: 0.0.0.0
- **포트**: 3000
- **외부 URL**: http://192.168.133.132:3000
- **프로덕션 모드**: true
- **로그 레벨**: info

### 관리자 계정
- **이메일**: admin@botpress.local
- **비밀번호**: Admin@2024! ⚠️ 변경 필수

---

## 보안 권장사항

### 🔒 필수 보안 조치

1. **비밀번호 변경**
   - [ ] Botpress 관리자 비밀번호 변경
   - [ ] PostgreSQL 비밀번호 변경
   - [ ] `docker-compose.yml`의 비밀번호 업데이트

2. **방화벽 설정**
   ```bash
   # 특정 네트워크만 허용
   sudo ufw allow from 192.168.133.0/24 to any port 3000
   ```

3. **HTTPS 설정** (프로덕션 환경)
   - Nginx 리버스 프록시 사용
   - Let's Encrypt SSL 인증서 적용

4. **정기 백업**
   ```bash
   # Cron 작업 추가
   0 2 * * * cd /opt/botpress && docker exec botpress-postgres pg_dump -U botpress botpress > /backup/botpress_$(date +\%Y\%m\%d).sql
   ```

---

## 성능 튜닝

### 리소스 할당 조정

`docker-compose.botpress.yml`에서 조정:

```yaml
services:
  botpress:
    deploy:
      resources:
        limits:
          cpus: '4'      # CPU 코어 수 증가
          memory: 4G     # 메모리 증가
```

### PostgreSQL 튜닝

```yaml
postgres:
  environment:
    POSTGRES_SHARED_BUFFERS: 256MB
    POSTGRES_EFFECTIVE_CACHE_SIZE: 1GB
```

---

## 업그레이드 가이드

### Botpress 버전 업그레이드

1. **백업 생성**
   ```bash
   cd /opt/botpress
   docker-compose exec postgres pg_dump -U botpress botpress > backup_before_upgrade.sql
   ```

2. **이미지 버전 변경**
   `docker-compose.yml`에서:
   ```yaml
   botpress:
     image: botpress/server:12.27.0  # 새 버전
   ```

3. **재시작**
   ```bash
   docker-compose down
   docker-compose pull
   docker-compose up -d
   ```

4. **확인**
   ```bash
   docker-compose logs -f botpress
   ```

---

## 모니터링

### 로그 모니터링

```bash
# 실시간 로그
docker-compose logs -f botpress

# 에러만 필터링
docker-compose logs botpress | grep ERROR

# 특정 시간대 로그
docker-compose logs --since "2024-12-22T10:00:00" botpress
```

### 리소스 모니터링

```bash
# 컨테이너 리소스 사용량
docker stats

# 디스크 사용량
docker system df

# 볼륨 크기
docker volume ls -q | xargs docker volume inspect | grep Mountpoint
```

---

## 추가 리소스

### 공식 문서
- 📖 Botpress v12 문서: https://v12.botpress.com/docs
- 🐳 Docker Hub: https://hub.docker.com/r/botpress/server
- 💬 커뮤니티 포럼: https://forum.botpress.com/

### 학습 자료
- 🎓 Botpress 튜토리얼: https://v12.botpress.com/tutorials
- 📺 YouTube 채널: Botpress Official
- 📚 GitHub 예제: https://github.com/botpress/botpress/tree/master/examples

---

## 지원 및 문의

### 문제 발생 시
1. 로그 확인
2. 상세 가이드 참조
3. 공식 포럼 검색
4. GitHub Issues 확인

### 연락처
- 프로젝트: AICC Chat
- 작성일: 2024-12-22
- 버전: 1.0.0

---

## 변경 이력

### 2024-12-22 - v1.0.0
- ✨ 초기 문서 생성
- 📚 상세 설치 가이드 작성
- 🚀 빠른 시작 가이드 작성
- 🐳 Docker Compose 설정 생성
- 🐧 Linux 설치 스크립트 작성
- 💻 Windows 설치 스크립트 작성
- 📖 README 업데이트

---

## 라이선스

Botpress v12는 AGPL-3.0 라이선스를 따릅니다.
상용 라이선스가 필요한 경우 Botpress 공식 웹사이트를 참조하세요.

---

**마지막 업데이트**: 2024-12-22
**작성자**: AICC Chat 개발팀
**버전**: 1.0.0

