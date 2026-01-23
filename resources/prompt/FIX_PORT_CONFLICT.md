# 🚨 포트 충돌 즉시 해결 가이드

## Redis 포트 6379 충돌 오류

```
failed to bind host port 0.0.0.0:6379/tcp: address already in use
```

---

## ⚡ 즉시 해결 (3가지 방법)

### 방법 1: 기존 Redis 중지 (가장 빠름)

```bash
# 1. 기존 Redis 컨테이너 확인
docker ps -a | grep redis

# 2. 기존 Redis 중지
docker stop redis
# 또는 컨테이너 이름이 다르면
docker stop <redis_container_name>

# 3. Botpress 재시작
cd /opt/botpress
docker compose up -d

# 4. 로그 확인
docker compose logs -f botpress
```

---

### 방법 2: Redis 없는 버전 사용 (권장) ⭐

Botpress는 Redis 없이도 완벽하게 작동합니다!

```bash
cd /opt/botpress

# 1. 현재 컨테이너 중지
docker compose down

# 2. 기존 파일 백업
mv docker-compose.yml docker-compose.yml.with-redis

# 3. Redis 없는 버전 사용
cp docker-compose.botpress-minimal.yml docker-compose.yml

# 4. 시작
docker compose up -d

# 5. 로그 확인
docker compose logs -f botpress
```

**또는 직접 수정:**
```bash
cd /opt/botpress
vi docker-compose.yml
```

**Redis 섹션 전체 삭제 (약 118-134 라인):**
- `/redis` 로 검색 (vi에서 `/` 키 누른 후 `redis` 입력)
- `dd` 키로 라인 삭제 (Redis 섹션 전체)
- `ESC` → `:wq` 로 저장

**volumes 섹션에서 redis_data도 삭제:**
```yaml
volumes:
  postgres_data:
    driver: local
  botpress_data:
    driver: local
  # redis_data: 삭제
```

---

### 방법 3: 다른 포트 사용

Redis를 유지하고 싶다면 포트만 변경:

```bash
cd /opt/botpress
vi docker-compose.yml
```

**Redis 포트 변경 (약 127 라인):**
```yaml
  redis:
    ports:
      - "6380:6379"  # 6379 → 6380으로 변경
```

**저장 후:**
```bash
docker compose down
docker compose up -d
```

---

## 🔍 포트 사용 확인

### 6379 포트 사용 중인 프로세스 확인
```bash
# Rocky Linux
sudo ss -tulpn | grep :6379

# 또는
sudo lsof -i :6379

# Docker 컨테이너 확인
docker ps | grep 6379
```

### 모든 Redis 컨테이너 확인
```bash
docker ps -a | grep redis
```

**출력 예:**
```
CONTAINER ID   IMAGE              PORTS                    NAMES
abc123def456   redis:7.2-alpine   0.0.0.0:6379->6379/tcp   redis
xyz789ghi012   redis:7.2-alpine   0.0.0.0:6379->6379/tcp   botpress-redis
```

---

## 📋 단계별 실행 (방법 2 상세)

### 서버에서 실행할 명령어:

```bash
# 1. 작업 디렉토리로 이동
cd /opt/botpress

# 2. 현재 실행 중인 컨테이너 중지
docker compose down

# 3. 기존 설정 백업
cp docker-compose.yml docker-compose.yml.backup

# 4. vi 에디터로 파일 열기
vi docker-compose.yml

# 5. Redis 섹션 찾기
# vi에서: /redis 입력 후 Enter

# 6. Redis 섹션 전체 삭제
# 커서를 redis: 라인으로 이동
# dd 키를 여러 번 눌러 Redis 섹션 전체 삭제 (약 15-20줄)

# 7. volumes 섹션으로 이동
# vi에서: /volumes 입력 후 Enter

# 8. redis_data 라인 삭제
# dd 키로 해당 라인 삭제

# 9. 저장 및 종료
# ESC 키 → :wq 입력 → Enter

# 10. 재시작
docker compose up -d

# 11. 로그 확인
docker compose logs -f botpress

# 12. 컨테이너 상태 확인
docker compose ps
```

---

## ✅ 성공 확인

### 예상 출력:
```bash
$ docker compose ps

NAME                  STATUS              PORTS
botpress-postgres     Up (healthy)        0.0.0.0:5432->5432/tcp
botpress-server       Up (healthy)        0.0.0.0:3000->3000/tcp
botpress-duckling     Up                  0.0.0.0:8000->8000/tcp
```

**Redis가 없어야 정상입니다!**

### 로그에서 확인:
```bash
$ docker compose logs botpress | tail -20

✓ Botpress is listening at: http://0.0.0.0:3000
✓ Botpress is exposed at: http://192.168.133.132:3000
```

### 웹 접속:
```
http://192.168.133.132:3000
```

---

## 🔄 원본 Redis와 Botpress Redis 구분

### AICC Chat의 Redis (기존)
```yaml
# docker-compose.yml (프로젝트 루트)
services:
  redis:
    image: redis:7.2-alpine
    ports:
      - '6379:6379'  # 이미 사용 중
```

### Botpress의 Redis (충돌)
```yaml
# /opt/botpress/docker-compose.yml
services:
  redis:
    image: redis:7.2-alpine
    ports:
      - "6379:6379"  # 충돌!
```

**해결책**: Botpress는 Redis가 필수가 아니므로 제거하는 것이 가장 간단합니다.

---

## 🆘 여전히 문제가 있다면

### 모든 컨테이너 확인
```bash
docker ps -a
```

### 특정 포트 사용 중인 모든 프로세스
```bash
sudo ss -tulpn | grep -E ':(3000|5432|6379|8000)'
```

### 완전 정리 후 재시작
```bash
cd /opt/botpress

# 모든 컨테이너 중지 및 제거
docker compose down -v

# 이미지 다시 받기
docker compose pull

# 시작
docker compose up -d

# 로그
docker compose logs -f
```

---

## 📞 추가 지원

- 🚨 **QUICK_FIX.md** - 다른 빠른 수정 방법
- 🔧 **BOTPRESS_TROUBLESHOOTING.md** - 전체 문제 해결
- 🐧 **ROCKY_LINUX_SETUP.md** - Rocky Linux 가이드

---

## 💡 팁

### Redis가 필요한가요?
**아니요!** Botpress v12는 다음만 필요합니다:
- ✅ PostgreSQL (데이터베이스)
- ✅ Duckling (NLU 엔티티 추출)
- ❌ Redis (선택사항, 세션 관리용)

기본 설정으로 Redis 없이 완벽하게 작동합니다.

---

**작성일**: 2024-12-22  
**긴급 수정**: Redis 포트 충돌

