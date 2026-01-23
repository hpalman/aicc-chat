# Rocky Linux 9.6 환경 Botpress 설치 완료 ✅

## 🎯 수정 완료 사항

Rocky Linux 9.6 (Blue Onyx) 환경에 맞게 모든 설치 가이드를 수정했습니다.

---

## 📦 수정된 파일 목록

### 1. ⭐ ROCKY_LINUX_SETUP.md (신규 생성)
**Rocky Linux 전용 완전 가이드**
- Rocky Linux vs Ubuntu 차이점 비교표
- dnf 패키지 관리자 사용법
- firewalld 방화벽 설정
- SELinux 설정 및 문제 해결
- vi 에디터 사용법
- Rocky Linux 특화 문제 해결

### 2. 📚 BOTPRESS_INSTALLATION_GUIDE.md (수정)
**변경 사항:**
- ✅ OS 정보: Rocky Linux 9.6으로 업데이트
- ✅ Docker 설치: apt-get → dnf 명령어로 변경
- ✅ 방화벽: ufw → firewalld 명령어로 변경
- ✅ SELinux 설정 섹션 추가
- ✅ 텍스트 에디터: nano → vi 사용법 추가
- ✅ vi 에디터 사용법 상세 설명

### 3. 🚀 BOTPRESS_QUICK_START.md (수정)
**변경 사항:**
- ✅ OS 정보 업데이트
- ✅ vi 에디터 사용법 추가
- ✅ Rocky Linux 문제 해결 섹션 추가
- ✅ firewalld 명령어 추가
- ✅ SELinux 문제 해결 추가
- ✅ ss 명령어 사용 (netstat 대체)

### 4. 🐧 setup-botpress.sh (수정)
**변경 사항:**
- ✅ OS 자동 감지 기능 추가
- ✅ Rocky Linux/RHEL 감지 시 dnf 사용
- ✅ firewalld 자동 설정
- ✅ SELinux 자동 설정 함수 추가
- ✅ 설치 과정에 SELinux 설정 단계 추가

### 5. 📖 README.md (수정)
**변경 사항:**
- ✅ Rocky Linux 9.6 정보 추가
- ✅ ROCKY_LINUX_SETUP.md 링크 추가 (⭐ 추천 표시)

---

## 🔧 주요 변경 내용

### 패키지 관리자
**이전 (Ubuntu):**
```bash
sudo apt-get update
sudo apt-get install -y docker-ce
```

**현재 (Rocky Linux):**
```bash
sudo dnf install -y dnf-plugins-core
sudo dnf install -y docker-ce
```

### 방화벽
**이전 (Ubuntu - UFW):**
```bash
sudo ufw allow 3000/tcp
sudo ufw status
```

**현재 (Rocky Linux - firewalld):**
```bash
sudo firewall-cmd --permanent --add-port=3000/tcp
sudo firewall-cmd --reload
sudo firewall-cmd --list-all
```

### 텍스트 에디터
**이전:**
```bash
nano docker-compose.yml
```

**현재:**
```bash
vi docker-compose.yml
# i: 입력 모드
# ESC: 명령 모드
# :wq: 저장 후 종료
```

### SELinux (신규 추가)
```bash
# SELinux 상태 확인
getenforce

# Docker 볼륨 컨텍스트 설정
sudo chcon -Rt svirt_sandbox_file_t /opt/botpress

# Container 관련 설정
sudo setsebool -P container_manage_cgroup on
```

---

## 🚀 빠른 시작 (Rocky Linux)

### 자동 설치 (권장)
```bash
# 1. 서버 접속
ssh user@192.168.133.132

# 2. 스크립트 실행
chmod +x setup-botpress.sh
./setup-botpress.sh
```

스크립트가 자동으로:
- ✅ Rocky Linux 감지
- ✅ Docker 설치 (dnf)
- ✅ firewalld 설정
- ✅ SELinux 설정
- ✅ Botpress 시작

### 수동 설치
상세한 수동 설치 방법은 [ROCKY_LINUX_SETUP.md](./ROCKY_LINUX_SETUP.md)를 참조하세요.

---

## 📚 문서 읽는 순서 (Rocky Linux 사용자)

### 처음 설치하는 경우
1. 🐧 **ROCKY_LINUX_SETUP.md** (Rocky Linux 전용, 필독!)
2. 📚 **BOTPRESS_INSTALLATION_GUIDE.md** (상세 가이드)
3. 🐳 **docker-compose.botpress.yml** (설정 파일)
4. 🐧 **setup-botpress.sh** 실행 (자동 설치)

### 빠르게 시작하는 경우
1. 🚀 **BOTPRESS_QUICK_START.md**
2. 🐧 **setup-botpress.sh** 실행

### 문제 해결이 필요한 경우
1. 🐧 **ROCKY_LINUX_SETUP.md** - 문제 해결 섹션
2. 🚀 **BOTPRESS_QUICK_START.md** - 빠른 문제 해결
3. 📚 **BOTPRESS_INSTALLATION_GUIDE.md** - 상세 문제 해결

---

## ⚠️ Rocky Linux 주의사항

### 1. nano 에디터 미설치
Rocky Linux에는 nano가 기본 설치되어 있지 않습니다.

**해결 방법:**
```bash
# nano 설치 (선택사항)
sudo dnf install -y nano

# 또는 vi 사용 (기본 설치됨)
vi filename
```

### 2. SELinux 활성화
Rocky Linux는 SELinux가 기본적으로 Enforcing 모드입니다.

**확인:**
```bash
getenforce
# 출력: Enforcing
```

**해결 방법:**
- 스크립트가 자동으로 설정
- 또는 수동으로 컨텍스트 설정 필요

### 3. firewalld 사용
ufw 대신 firewalld를 사용합니다.

**기본 명령어:**
```bash
sudo firewall-cmd --list-all
sudo firewall-cmd --permanent --add-port=3000/tcp
sudo firewall-cmd --reload
```

### 4. dnf 패키지 관리자
apt-get 대신 dnf를 사용합니다.

**기본 명령어:**
```bash
sudo dnf update
sudo dnf install -y package-name
sudo dnf remove -y package-name
sudo dnf search package-name
```

---

## 🔍 일반적인 문제 해결

### 문제 1: Permission Denied (Docker)
```bash
# 해결
sudo usermod -aG docker $USER
newgrp docker
```

### 문제 2: 방화벽으로 접속 불가
```bash
# 확인
sudo firewall-cmd --list-all

# 해결
sudo firewall-cmd --permanent --add-port=3000/tcp
sudo firewall-cmd --reload
```

### 문제 3: SELinux 차단
```bash
# 확인
sudo ausearch -m avc -ts recent

# 해결 (임시)
sudo setenforce 0

# 해결 (영구)
sudo chcon -Rt svirt_sandbox_file_t /opt/botpress
sudo setsebool -P container_manage_cgroup on
```

### 문제 4: 포트 충돌
```bash
# 확인
sudo ss -tulpn | grep :3000

# 해결: 프로세스 종료 또는 다른 포트 사용
```

---

## ✅ 설치 확인 체크리스트

### 시스템 확인
- [ ] OS 확인: `cat /etc/os-release` → Rocky Linux 9.6
- [ ] Docker 설치: `docker --version`
- [ ] Docker Compose: `docker compose version`

### 방화벽 확인
- [ ] firewalld 실행: `sudo systemctl status firewalld`
- [ ] 포트 개방: `sudo firewall-cmd --list-all`
- [ ] 3000, 5432, 8000 포트 확인

### SELinux 확인
- [ ] SELinux 상태: `getenforce`
- [ ] 컨텍스트 설정: `ls -Z /opt/botpress`

### Botpress 확인
- [ ] 컨테이너 실행: `docker compose ps`
- [ ] 로그 정상: `docker compose logs botpress`
- [ ] 웹 접속: http://192.168.133.132:3000
- [ ] 로그인 성공: admin@botpress.local

---

## 📞 지원 및 문서

### Rocky Linux 관련
- 🐧 **ROCKY_LINUX_SETUP.md** - Rocky Linux 전용 가이드
- 📖 Rocky Linux 공식 문서: https://docs.rockylinux.org/

### Botpress 관련
- 📚 **BOTPRESS_INSTALLATION_GUIDE.md** - 전체 설치 가이드
- 🚀 **BOTPRESS_QUICK_START.md** - 빠른 시작
- 💬 Botpress 포럼: https://forum.botpress.com/

### 문제 해결
1. 로그 확인: `docker compose logs -f`
2. 시스템 로그: `sudo journalctl -u docker`
3. SELinux 로그: `sudo ausearch -m avc -ts recent`

---

## 🎉 설치 완료 후

### 접속 정보
```
URL: http://192.168.133.132:3000
이메일: admin@botpress.local
비밀번호: Admin@2024!
```

### 다음 단계
1. ✅ 로그인
2. ✅ 비밀번호 변경
3. ✅ 첫 번째 봇 생성
4. ✅ 워크플로우 작성
5. ✅ AICC Chat 시스템과 통합

---

## 📝 변경 이력

### 2024-12-22 - Rocky Linux 9.6 지원 추가
- ✨ ROCKY_LINUX_SETUP.md 신규 생성
- 🔧 모든 가이드 Rocky Linux 환경에 맞게 수정
- 🐧 setup-botpress.sh OS 자동 감지 기능 추가
- 🔥 firewalld 설정 추가
- 🔒 SELinux 설정 추가
- 📝 vi 에디터 사용법 추가

---

**작성일**: 2024-12-22  
**대상 OS**: Rocky Linux 9.6 (Blue Onyx)  
**서버 IP**: 192.168.133.132  
**Botpress 버전**: v12.26.11

