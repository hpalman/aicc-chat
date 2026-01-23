# Botpress v12 빠른 시작 가이드

## 🚀 5분 안에 시작하기

### 전제 조건
- Docker 및 Docker Compose 설치됨
- 서버 IP: 192.168.133.132
- OS: Rocky Linux 9.6 (Blue Onyx)

---

## 방법 1: 자동 설치 스크립트 사용 (권장)

### Rocky Linux 9.6 서버
```bash
# 1. 서버 접속
ssh user@192.168.133.132

# 2. 스크립트 실행 권한 부여 및 실행
chmod +x setup-botpress.sh
./setup-botpress.sh

# 스크립트가 자동으로 수행하는 작업:
# - OS 감지 (Rocky Linux)
# - Docker 설치 (dnf 사용)
# - 방화벽 설정 (firewalld)
# - SELinux 설정
# - Botpress 시작
```

### Windows에서 원격 설치
```powershell
# PowerShell 관리자 권한으로 실행
.\setup-botpress.ps1
```

---

## 방법 2: 수동 설치 (3단계)

### Step 1: 파일 준비
```bash
ssh user@192.168.133.132
mkdir -p /opt/botpress
cd /opt/botpress
```

### Step 2: Docker Compose 파일 생성
```bash
# vi 에디터 사용 (Rocky Linux에는 nano가 기본 설치되어 있지 않음)
vi docker-compose.yml

# vi 사용법:
# - i 키: 입력 모드
# - ESC 키: 명령 모드
# - :wq 입력 후 Enter: 저장 후 종료
# - :q! 입력 후 Enter: 저장하지 않고 종료

# 또는 nano 설치 후 사용
sudo dnf install -y nano
nano docker-compose.yml
```

다음 내용을 복사하여 붙여넣기:
```yaml
version: '3.8'
services:
  postgres:
    image: postgres:13-alpine
    environment:
      POSTGRES_DB: botpress
      POSTGRES_USER: botpress
      POSTGRES_PASSWORD: botpress_secure_password_2024
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - botpress-network

  botpress:
    image: botpress/server:12.26.11
    depends_on:
      - postgres
    environment:
      DATABASE_URL: postgres://botpress:botpress_secure_password_2024@postgres:5432/botpress
      EXTERNAL_URL: http://192.168.133.132:3000
      BP_PRODUCTION: "true"
    ports:
      - "3000:3000"
    volumes:
      - botpress_data:/botpress/data
    networks:
      - botpress-network

  duckling:
    image: rasa/duckling:latest
    ports:
      - "8000:8000"
    networks:
      - botpress-network

networks:
  botpress-network:
volumes:
  postgres_data:
  botpress_data:
```

### Step 3: 시작
```bash
docker-compose up -d
docker-compose logs -f botpress
```

---

## 접속 정보

### 웹 인터페이스
```
URL: http://192.168.133.132:3000
```

### 초기 로그인
```
이메일: admin@botpress.local
비밀번호: Admin@2024!
```

⚠️ **보안**: 첫 로그인 후 즉시 비밀번호를 변경하세요!

---

## 기본 명령어

### 상태 확인
```bash
cd /opt/botpress
docker-compose ps
docker-compose logs -f botpress
```

### 제어
```bash
# 시작
docker-compose start

# 중지
docker-compose stop

# 재시작
docker-compose restart botpress

# 완전 중지 및 제거
docker-compose down

# 볼륨까지 제거 (주의!)
docker-compose down -v
```

### 로그 확인
```bash
# 실시간 로그
docker-compose logs -f botpress

# 최근 100줄
docker-compose logs --tail=100 botpress

# 특정 시간 이후
docker-compose logs --since 30m botpress
```

---

## 첫 번째 봇 만들기

### 1. 봇 생성
1. http://192.168.133.132:3000 접속
2. 로그인
3. **Create Bot** 클릭
4. 봇 이름 입력 (예: `my-first-bot`)
5. **Create** 클릭

### 2. 간단한 대화 만들기
1. **Open in Studio** 클릭
2. 좌측 **Flows** 선택
3. `main.flow.json` 선택
4. 노드 추가 및 연결
5. 우측 상단 **Publish** 클릭

### 3. 테스트
1. 우측 하단 **Emulator** 아이콘 클릭
2. 메시지 입력하여 테스트

---

## 문제 해결

### 컨테이너가 시작되지 않음
```bash
# 로그 확인
docker-compose logs botpress

# 재시작
docker-compose restart

# 완전 재생성
docker-compose down
docker-compose up -d
```

### 포트 충돌
```bash
# 포트 사용 확인 (Rocky Linux)
sudo ss -tulpn | grep :3000
# 또는
sudo netstat -tulpn | grep :3000

# 다른 포트 사용 (docker-compose.yml 수정)
ports:
  - "3001:3000"
```

### 데이터베이스 연결 오류
```bash
# PostgreSQL 상태 확인
docker-compose ps postgres
docker-compose logs postgres

# 재시작
docker-compose restart postgres
```

### 방화벽 문제 (Rocky Linux)
```bash
# 방화벽 상태 확인
sudo firewall-cmd --list-all

# 포트가 개방되지 않았다면
sudo firewall-cmd --permanent --add-port=3000/tcp
sudo firewall-cmd --reload

# 방화벽 비활성화 (테스트용, 권장하지 않음)
sudo systemctl stop firewalld
```

### SELinux 문제 (Rocky Linux)
```bash
# SELinux 상태 확인
getenforce

# SELinux가 문제를 일으킨다면 (로그에서 확인)
sudo ausearch -m avc -ts recent

# 임시로 Permissive 모드로 전환 (테스트용)
sudo setenforce 0

# Docker 볼륨 컨텍스트 재설정
sudo chcon -Rt svirt_sandbox_file_t /opt/botpress

# 영구적으로 설정하려면
sudo vi /etc/selinux/config
# SELINUX=permissive 로 변경 (재부팅 필요)
```

### Docker 권한 문제
```bash
# 현재 사용자를 docker 그룹에 추가
sudo usermod -aG docker $USER

# 즉시 적용
newgrp docker

# 또는 로그아웃 후 재로그인
```

---

## 다음 단계

### 학습 자료
- 📚 상세 가이드: `BOTPRESS_INSTALLATION_GUIDE.md`
- 🌐 공식 문서: https://v12.botpress.com/docs
- 💬 커뮤니티: https://forum.botpress.com/

### 고급 기능
- NLU (자연어 이해) 설정
- 워크플로우 고급 기능
- 외부 API 통합
- 채널 통합 (웹, 메신저 등)

---

## 백업 및 복구

### 백업
```bash
# PostgreSQL 백업
docker exec botpress-postgres pg_dump -U botpress botpress > backup_$(date +%Y%m%d).sql

# 데이터 볼륨 백업
docker run --rm -v botpress_botpress_data:/data -v $(pwd):/backup alpine \
  tar czf /backup/botpress_data_$(date +%Y%m%d).tar.gz /data
```

### 복원
```bash
# PostgreSQL 복원
cat backup_20241222.sql | docker exec -i botpress-postgres psql -U botpress -d botpress

# 데이터 볼륨 복원
docker run --rm -v botpress_botpress_data:/data -v $(pwd):/backup alpine \
  tar xzf /backup/botpress_data_20241222.tar.gz -C /
```

---

## 성능 최적화

### 리소스 제한 설정
`docker-compose.yml`에 추가:
```yaml
services:
  botpress:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '1'
          memory: 1G
```

### 로그 레벨 조정
```yaml
environment:
  VERBOSITY_LEVEL: "warn"  # debug, info, warn, error
```

---

## 보안 체크리스트

- [ ] 관리자 비밀번호 변경
- [ ] PostgreSQL 비밀번호 변경
- [ ] 방화벽 설정 (필요한 포트만 개방)
- [ ] HTTPS 설정 (프로덕션 환경)
- [ ] 정기 백업 설정
- [ ] 로그 모니터링 설정

---

## 지원

### 문제 발생 시
1. 로그 확인: `docker-compose logs -f botpress`
2. 상세 가이드 참조: `BOTPRESS_INSTALLATION_GUIDE.md`
3. 공식 포럼: https://forum.botpress.com/
4. GitHub Issues: https://github.com/botpress/botpress/issues

### 연락처
- 프로젝트: AICC Chat
- 작성일: 2024-12-22

---

## 버전 정보
- Botpress: v12.26.11
- PostgreSQL: 13-alpine
- Duckling: latest
- Docker Compose: 3.8

