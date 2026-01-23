# Rocky Linux 9.6에서 Botpress 설치 가이드

## 🐧 Rocky Linux 특화 가이드

이 문서는 **Rocky Linux 9.6 (Blue Onyx)** 환경에서 Botpress v12를 설치하는 방법을 설명합니다.

---

## 📋 Rocky Linux vs Ubuntu 차이점

| 항목 | Rocky Linux 9.6 | Ubuntu 20.04+ |
|------|-----------------|---------------|
| 패키지 관리자 | `dnf` | `apt-get` |
| 방화벽 | `firewalld` | `ufw` |
| SELinux | 기본 활성화 (Enforcing) | 기본 비활성화 |
| 텍스트 에디터 | `vi/vim` (기본) | `nano` (기본) |
| 베이스 | RHEL 9 호환 | Debian 기반 |

---

## 🚀 빠른 설치 (자동)

### 1단계: 스크립트 실행
```bash
# 서버 접속
ssh user@192.168.133.132

# 스크립트 실행
chmod +x setup-botpress.sh
./setup-botpress.sh
```

스크립트가 자동으로:
- ✅ Rocky Linux 감지
- ✅ Docker 설치 (dnf 사용)
- ✅ firewalld 설정
- ✅ SELinux 설정
- ✅ Botpress 시작

---

## 🔧 수동 설치

### 1. Docker 설치

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

# Docker 서비스 시작
sudo systemctl start docker
sudo systemctl enable docker

# 사용자를 docker 그룹에 추가
sudo usermod -aG docker $USER

# 즉시 적용
newgrp docker

# 설치 확인
docker --version
docker compose version
```

### 2. 방화벽 설정 (firewalld)

```bash
# firewalld 시작
sudo systemctl start firewalld
sudo systemctl enable firewalld

# 포트 개방
sudo firewall-cmd --permanent --add-port=3000/tcp  # Botpress
sudo firewall-cmd --permanent --add-port=5432/tcp  # PostgreSQL
sudo firewall-cmd --permanent --add-port=8000/tcp  # Duckling

# 규칙 적용
sudo firewall-cmd --reload

# 확인
sudo firewall-cmd --list-all
```

### 3. SELinux 설정

```bash
# 현재 상태 확인
getenforce

# 옵션 1: Permissive 모드로 전환 (테스트용)
sudo setenforce 0

# 옵션 2: Docker 볼륨에 컨텍스트 설정 (권장)
sudo chcon -Rt svirt_sandbox_file_t /opt/botpress

# Container 관련 boolean 설정
sudo setsebool -P container_manage_cgroup on

# 영구적으로 Permissive 모드 설정 (선택사항)
sudo vi /etc/selinux/config
# SELINUX=permissive 로 변경
```

### 4. 작업 디렉토리 생성

```bash
# 디렉토리 생성
sudo mkdir -p /opt/botpress
cd /opt/botpress

# 권한 설정
sudo chown -R $USER:$USER /opt/botpress
```

### 5. Docker Compose 파일 생성

```bash
# vi 에디터로 파일 생성
vi docker-compose.yml
```

**vi 에디터 사용법:**
1. `i` 키를 눌러 입력 모드로 전환
2. 아래 내용을 붙여넣기 (Shift+Insert 또는 마우스 우클릭)
3. `ESC` 키를 눌러 명령 모드로 전환
4. `:wq` 입력 후 Enter (저장 후 종료)

**Docker Compose 내용:**
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
      DATABASE_URL: postgres://botpress:botpress_secure_password_2024@postgres:5432/botpress
      BP_HOST: 0.0.0.0
      BP_PORT: 3000
      EXTERNAL_URL: http://192.168.133.132:3000
      BP_PRODUCTION: "true"
      VERBOSITY_LEVEL: "info"
      BP_MODULE_NLU_DUCKLINGURL: http://duckling:8000
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
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '1'
          memory: 1G

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

### 6. Botpress 시작

```bash
# 컨테이너 시작
docker compose up -d

# 로그 확인
docker compose logs -f botpress

# 상태 확인
docker compose ps
```

---

## 🔍 문제 해결

### 0. Botpress 설정 오류 (환경 변수)

**증상:**
```
ConfigProvider (Deprecated) use standard syntax: BP_PORT ==> BP_CONFIG_HTTPSERVER_PORT
Error while loading module MODULES_ROOT/qna
Cannot read property 'useCookieStorage' of undefined
```

**원인:**
- 환경 변수 형식이 올바르지 않음
- 필수 설정이 누락됨

**해결:**

Docker Compose 파일의 환경 변수를 다음과 같이 수정:

```yaml
environment:
  # 올바른 HTTP 서버 설정
  BP_CONFIG_HTTPSERVER_HOST: 0.0.0.0
  BP_CONFIG_HTTPSERVER_PORT: 3000
  BP_CONFIG_HTTPSERVER_BACKLOG: 511
  BP_CONFIG_HTTPSERVER_BODYLIMIT: 100mb
  BP_CONFIG_HTTPSERVER_CORS_ENABLED: "true"
  
  # 데이터베이스
  DATABASE_URL: postgres://botpress:password@postgres:5432/botpress
  
  # 인증 설정 (필수!)
  BP_CONFIG_JWTTOKEN_SECRET: "change-this-secret-in-production"
  BP_CONFIG_JWTTOKEN_DURATION: "6h"
  BP_CONFIG_PRO_ENABLED: "false"
  
  # 모듈 설정
  BP_MODULE_NLU_ENABLED: "true"
  BP_MODULE_BUILTIN_ENABLED: "true"
  BP_MODULE_CHANNEL_WEB_ENABLED: "true"
  
  # 외부 URL
  EXTERNAL_URL: http://192.168.133.132:3000
  
  # 프로덕션 모드
  BP_PRODUCTION: "true"
  VERBOSITY_LEVEL: "info"
```

**재시작:**
```bash
cd /opt/botpress
docker compose down
docker compose up -d
docker compose logs -f botpress
```

### 1. Docker 명령어 권한 오류

**증상:**
```
permission denied while trying to connect to the Docker daemon socket
```

**해결:**
```bash
# 사용자를 docker 그룹에 추가
sudo usermod -aG docker $USER

# 즉시 적용
newgrp docker

# 또는 로그아웃 후 재로그인
```

### 2. 방화벽으로 인한 접속 불가

**증상:**
- 웹 브라우저에서 http://192.168.133.132:3000 접속 불가

**확인:**
```bash
# 방화벽 상태 확인
sudo firewall-cmd --list-all

# 포트 3000이 목록에 없다면
sudo firewall-cmd --permanent --add-port=3000/tcp
sudo firewall-cmd --reload
```

### 3. SELinux 차단

**증상:**
```
docker: Error response from daemon: failed to create shim task
```

**확인:**
```bash
# SELinux 로그 확인
sudo ausearch -m avc -ts recent

# 또는
sudo tail -f /var/log/audit/audit.log | grep denied
```

**해결 방법 1: 임시 (테스트용)**
```bash
sudo setenforce 0
docker compose restart
```

**해결 방법 2: 영구 (권장)**
```bash
# Docker 볼륨 컨텍스트 설정
sudo chcon -Rt svirt_sandbox_file_t /opt/botpress

# SELinux boolean 설정
sudo setsebool -P container_manage_cgroup on

# 재시작
docker compose restart
```

**해결 방법 3: SELinux 비활성화 (권장하지 않음)**
```bash
sudo vi /etc/selinux/config
# SELINUX=disabled 로 변경
sudo reboot
```

### 4. 포트 충돌

**증상:**
```
Bind for 0.0.0.0:3000 failed: port is already allocated
```

**확인:**
```bash
# 포트 사용 확인
sudo ss -tulpn | grep :3000
```

**해결:**
```bash
# 기존 프로세스 종료 또는
# docker-compose.yml에서 다른 포트 사용
ports:
  - "3001:3000"
```

### 5. 컨테이너 시작 실패

**로그 확인:**
```bash
# 전체 로그
docker compose logs

# 특정 서비스
docker compose logs botpress
docker compose logs postgres

# 실시간 로그
docker compose logs -f
```

**일반적인 해결 방법:**
```bash
# 컨테이너 재시작
docker compose restart

# 완전히 재생성
docker compose down
docker compose up -d

# 이미지 다시 받기
docker compose pull
docker compose up -d
```

### 6. PostgreSQL 연결 오류

**증상:**
```
Error: connect ECONNREFUSED
```

**확인:**
```bash
# PostgreSQL 컨테이너 상태
docker compose ps postgres

# PostgreSQL 로그
docker compose logs postgres

# PostgreSQL 접속 테스트
docker exec -it botpress-postgres psql -U botpress -d botpress
```

**해결:**
```bash
# PostgreSQL 재시작
docker compose restart postgres

# 헬스체크 대기
docker compose ps
```

---

## 📝 유용한 명령어

### 시스템 정보
```bash
# OS 버전 확인
cat /etc/os-release

# 커널 버전
uname -r

# 메모리 확인
free -h

# 디스크 확인
df -h
```

### Docker 관리
```bash
# Docker 버전
docker --version
docker compose version

# 실행 중인 컨테이너
docker ps

# 모든 컨테이너
docker ps -a

# 리소스 사용량
docker stats

# 디스크 사용량
docker system df

# 정리
docker system prune -a
```

### 방화벽 관리
```bash
# 현재 규칙
sudo firewall-cmd --list-all

# 포트 추가
sudo firewall-cmd --permanent --add-port=PORT/tcp
sudo firewall-cmd --reload

# 포트 제거
sudo firewall-cmd --permanent --remove-port=PORT/tcp
sudo firewall-cmd --reload

# 서비스 추가
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --reload
```

### SELinux 관리
```bash
# 상태 확인
getenforce
sestatus

# 모드 변경 (임시)
sudo setenforce 0  # Permissive
sudo setenforce 1  # Enforcing

# 로그 확인
sudo ausearch -m avc -ts recent
sudo tail -f /var/log/audit/audit.log

# 컨텍스트 확인
ls -Z /opt/botpress
```

### 로그 관리
```bash
# journalctl로 시스템 로그
sudo journalctl -u docker
sudo journalctl -u firewalld
sudo journalctl -f  # 실시간

# Docker 로그
docker compose logs -f
docker logs botpress-server
```

---

## 🔒 보안 권장사항

### 1. 방화벽 설정
```bash
# 특정 IP만 허용
sudo firewall-cmd --permanent --add-rich-rule='rule family="ipv4" source address="192.168.133.0/24" port port="3000" protocol="tcp" accept'
sudo firewall-cmd --reload
```

### 2. SELinux 유지
- 가능하면 SELinux를 Enforcing 모드로 유지
- 필요한 컨텍스트만 추가

### 3. 정기 업데이트
```bash
# 시스템 업데이트
sudo dnf update -y

# Docker 업데이트
sudo dnf update docker-ce docker-ce-cli containerd.io
```

### 4. 비밀번호 변경
- Botpress 관리자 비밀번호
- PostgreSQL 비밀번호
- docker-compose.yml의 모든 비밀번호

---

## 📚 추가 리소스

### Rocky Linux 문서
- 공식 문서: https://docs.rockylinux.org/
- Wiki: https://wiki.rockylinux.org/

### Docker on RHEL/Rocky
- Docker 공식 문서: https://docs.docker.com/engine/install/rhel/
- Red Hat Container Tools: https://access.redhat.com/documentation/en-us/red_hat_enterprise_linux/9/html/building_running_and_managing_containers/

### SELinux 가이드
- SELinux 사용자 가이드: https://access.redhat.com/documentation/en-us/red_hat_enterprise_linux/9/html/using_selinux/
- Docker와 SELinux: https://docs.docker.com/storage/bind-mounts/#configure-the-selinux-label

---

## ✅ 설치 확인 체크리스트

- [ ] Docker 설치 확인: `docker --version`
- [ ] Docker Compose 설치 확인: `docker compose version`
- [ ] 방화벽 포트 개방 확인: `sudo firewall-cmd --list-all`
- [ ] SELinux 설정 확인: `getenforce`
- [ ] 컨테이너 실행 확인: `docker compose ps`
- [ ] Botpress 접속 확인: http://192.168.133.132:3000
- [ ] 로그인 테스트: admin@botpress.local / Admin@2024!
- [ ] 비밀번호 변경 완료

---

## 🆘 지원

문제가 계속되면:
1. 로그 확인: `docker compose logs -f`
2. 상세 가이드: `BOTPRESS_INSTALLATION_GUIDE.md`
3. 빠른 참조: `BOTPRESS_QUICK_START.md`
4. Botpress 포럼: https://forum.botpress.com/

---

**작성일**: 2024-12-22  
**대상 OS**: Rocky Linux 9.6 (Blue Onyx)  
**Botpress 버전**: v12.26.11

