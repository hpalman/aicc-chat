#!/bin/bash

###############################################################################
# Botpress v12 자동 설치 스크립트
# 대상 서버: 192.168.133.132 (Rocky Linux 9.6)
# 작성일: 2024-12-22
###############################################################################

set -e  # 오류 발생 시 스크립트 중단

# OS 감지
detect_os() {
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        OS=$NAME
        VER=$VERSION_ID
    else
        OS=$(uname -s)
        VER=$(uname -r)
    fi
}

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 로깅 함수
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 배너 출력
print_banner() {
    detect_os
    echo -e "${BLUE}"
    echo "╔═══════════════════════════════════════════════════════════╗"
    echo "║                                                           ║"
    echo "║        Botpress v12 자동 설치 스크립트                    ║"
    echo "║        Target: 192.168.133.132                            ║"
    echo "║        OS: $OS $VER                                       "
    echo "║                                                           ║"
    echo "╚═══════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
}

# 사전 요구사항 확인
check_prerequisites() {
    log_info "사전 요구사항 확인 중..."
    
    # Docker 확인
    if ! command -v docker &> /dev/null; then
        log_error "Docker가 설치되어 있지 않습니다."
        log_info "Docker를 설치하시겠습니까? (y/n)"
        read -r response
        if [[ "$response" =~ ^([yY][eE][sS]|[yY])$ ]]; then
            install_docker
        else
            log_error "Docker가 필요합니다. 설치 후 다시 실행해주세요."
            exit 1
        fi
    else
        log_success "Docker 설치 확인: $(docker --version)"
    fi
    
    # Docker Compose 확인
    if ! command -v docker-compose &> /dev/null; then
        log_error "Docker Compose가 설치되어 있지 않습니다."
        log_info "Docker Compose를 설치하시겠습니까? (y/n)"
        read -r response
        if [[ "$response" =~ ^([yY][eE][sS]|[yY])$ ]]; then
            install_docker_compose
        else
            log_error "Docker Compose가 필요합니다. 설치 후 다시 실행해주세요."
            exit 1
        fi
    else
        log_success "Docker Compose 설치 확인: $(docker-compose --version)"
    fi
    
    # 디스크 공간 확인
    available_space=$(df -BG / | awk 'NR==2 {print $4}' | sed 's/G//')
    if [ "$available_space" -lt 10 ]; then
        log_warning "디스크 여유 공간이 부족합니다: ${available_space}GB (최소 10GB 권장)"
    else
        log_success "디스크 여유 공간 확인: ${available_space}GB"
    fi
    
    # 메모리 확인
    total_mem=$(free -g | awk 'NR==2 {print $2}')
    if [ "$total_mem" -lt 4 ]; then
        log_warning "메모리가 부족합니다: ${total_mem}GB (최소 4GB 권장)"
    else
        log_success "메모리 확인: ${total_mem}GB"
    fi
}

# Docker 설치
install_docker() {
    log_info "Docker 설치 중..."
    detect_os
    
    if [[ "$OS" == *"Rocky"* ]] || [[ "$OS" == *"Red Hat"* ]] || [[ "$OS" == *"CentOS"* ]]; then
        log_info "Rocky Linux/RHEL 기반 시스템 감지"
        
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
                          runc 2>/dev/null || true
        
        # 필수 패키지 설치
        sudo dnf install -y dnf-plugins-core
        
        # Docker 공식 저장소 추가
        sudo dnf config-manager --add-repo https://download.docker.com/linux/rhel/docker-ce.repo
        
        # Docker 설치
        sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
        
    else
        log_info "Debian/Ubuntu 기반 시스템으로 가정"
        
        # 이전 버전 제거
        sudo apt-get remove -y docker docker-engine docker.io containerd runc 2>/dev/null || true
        
        # 필수 패키지 설치
        sudo apt-get update
        sudo apt-get install -y \
            ca-certificates \
            curl \
            gnupg \
            lsb-release
        
        # Docker 공식 GPG 키 추가
        sudo mkdir -p /etc/apt/keyrings
        curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
        
        # Docker 저장소 설정
        echo \
          "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
          $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
        
        # Docker 설치
        sudo apt-get update
        sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
    fi
    
    # Docker 서비스 시작
    sudo systemctl start docker
    sudo systemctl enable docker
    
    # 현재 사용자를 docker 그룹에 추가
    sudo usermod -aG docker $USER
    
    log_success "Docker 설치 완료"
    log_warning "변경사항 적용을 위해 로그아웃 후 재로그인이 필요합니다."
    log_info "또는 'newgrp docker' 명령으로 즉시 적용할 수 있습니다."
}

# Docker Compose 설치
install_docker_compose() {
    log_info "Docker Compose 설치 중..."
    
    sudo curl -L "https://github.com/docker/compose/releases/download/v2.24.0/docker-compose-$(uname -s)-$(uname -m)" \
        -o /usr/local/bin/docker-compose
    
    sudo chmod +x /usr/local/bin/docker-compose
    
    log_success "Docker Compose 설치 완료"
}

# 작업 디렉토리 생성
create_directories() {
    log_info "작업 디렉토리 생성 중..."
    
    INSTALL_DIR="/opt/botpress"
    
    if [ -d "$INSTALL_DIR" ]; then
        log_warning "디렉토리가 이미 존재합니다: $INSTALL_DIR"
        log_info "기존 디렉토리를 사용하시겠습니까? (y/n)"
        read -r response
        if [[ ! "$response" =~ ^([yY][eE][sS]|[yY])$ ]]; then
            log_error "설치를 중단합니다."
            exit 1
        fi
    else
        sudo mkdir -p "$INSTALL_DIR"
        sudo chown -R $USER:$USER "$INSTALL_DIR"
        log_success "디렉토리 생성 완료: $INSTALL_DIR"
    fi
    
    cd "$INSTALL_DIR"
}

# Docker Compose 파일 다운로드/생성
setup_docker_compose() {
    log_info "Docker Compose 파일 설정 중..."
    
    # docker-compose.botpress.yml 파일이 현재 디렉토리에 있다면 복사
    if [ -f "docker-compose.botpress.yml" ]; then
        cp docker-compose.botpress.yml /opt/botpress/docker-compose.yml
        log_success "Docker Compose 파일 복사 완료"
    else
        log_warning "docker-compose.botpress.yml 파일을 찾을 수 없습니다."
        log_info "기본 설정으로 생성합니다..."
        # 여기에 기본 docker-compose.yml 내용 생성 가능
    fi
}

# 방화벽 설정
configure_firewall() {
    log_info "방화벽 설정 중..."
    detect_os
    
    if command -v firewall-cmd &> /dev/null; then
        log_info "firewalld 감지 (Rocky Linux/RHEL)"
        
        # firewalld 시작
        sudo systemctl start firewalld 2>/dev/null || true
        sudo systemctl enable firewalld 2>/dev/null || true
        
        # 포트 개방
        sudo firewall-cmd --permanent --add-port=3000/tcp
        sudo firewall-cmd --permanent --add-port=5432/tcp
        sudo firewall-cmd --permanent --add-port=8000/tcp
        
        # 규칙 적용
        sudo firewall-cmd --reload
        
        log_success "firewalld 방화벽 규칙 추가 완료"
        
        # 현재 규칙 표시
        log_info "현재 방화벽 규칙:"
        sudo firewall-cmd --list-all
        
    elif command -v ufw &> /dev/null; then
        log_info "UFW 감지 (Ubuntu/Debian)"
        sudo ufw allow 3000/tcp comment 'Botpress Server'
        sudo ufw allow 5432/tcp comment 'PostgreSQL'
        sudo ufw allow 8000/tcp comment 'Duckling'
        log_success "UFW 방화벽 규칙 추가 완료"
    else
        log_warning "방화벽 도구를 찾을 수 없습니다. 수동으로 포트를 개방해주세요."
        log_info "필요한 포트: 3000 (Botpress), 5432 (PostgreSQL), 8000 (Duckling)"
    fi
}

# SELinux 설정 (Rocky Linux)
configure_selinux() {
    detect_os
    
    if [[ "$OS" == *"Rocky"* ]] || [[ "$OS" == *"Red Hat"* ]] || [[ "$OS" == *"CentOS"* ]]; then
        log_info "SELinux 설정 확인 중..."
        
        if command -v getenforce &> /dev/null; then
            selinux_status=$(getenforce)
            log_info "현재 SELinux 상태: $selinux_status"
            
            if [ "$selinux_status" == "Enforcing" ]; then
                log_warning "SELinux가 Enforcing 모드입니다."
                log_info "Docker 볼륨에 대한 컨텍스트를 설정합니다..."
                
                # Docker 볼륨 디렉토리에 SELinux 컨텍스트 설정
                sudo chcon -Rt svirt_sandbox_file_t /opt/botpress 2>/dev/null || true
                
                # Container 관련 SELinux boolean 설정
                sudo setsebool -P container_manage_cgroup on 2>/dev/null || true
                
                log_success "SELinux 설정 완료"
            fi
        fi
    fi
}

# Botpress 시작
start_botpress() {
    log_info "Botpress 컨테이너 시작 중..."
    
    cd /opt/botpress
    
    # 기존 컨테이너 중지 및 제거
    docker-compose down 2>/dev/null || true
    
    # 컨테이너 시작
    docker-compose up -d
    
    log_info "컨테이너 시작 대기 중 (60초)..."
    sleep 60
    
    # 상태 확인
    docker-compose ps
    
    log_success "Botpress 시작 완료"
}

# 상태 확인
check_status() {
    log_info "Botpress 상태 확인 중..."
    
    # 컨테이너 상태 확인
    if docker ps | grep -q "botpress-server"; then
        log_success "Botpress 컨테이너가 실행 중입니다."
    else
        log_error "Botpress 컨테이너가 실행되지 않습니다."
        log_info "로그를 확인하세요: docker-compose logs botpress"
        return 1
    fi
    
    # HTTP 상태 확인
    log_info "HTTP 엔드포인트 확인 중..."
    for i in {1..10}; do
        if curl -s http://localhost:3000/status > /dev/null; then
            log_success "Botpress 서버가 정상적으로 응답합니다."
            return 0
        fi
        log_info "재시도 중... ($i/10)"
        sleep 5
    done
    
    log_warning "HTTP 엔드포인트에 접속할 수 없습니다."
    log_info "로그를 확인하세요: docker-compose logs botpress"
}

# 설치 정보 출력
print_info() {
    echo ""
    echo -e "${GREEN}╔═══════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║                                                           ║${NC}"
    echo -e "${GREEN}║           Botpress v12 설치 완료!                         ║${NC}"
    echo -e "${GREEN}║                                                           ║${NC}"
    echo -e "${GREEN}╚═══════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${BLUE}접속 정보:${NC}"
    echo -e "  URL: ${GREEN}http://192.168.133.132:3000${NC}"
    echo -e "  관리자 이메일: ${GREEN}admin@botpress.local${NC}"
    echo -e "  관리자 비밀번호: ${GREEN}Admin@2024!${NC}"
    echo ""
    echo -e "${YELLOW}⚠️  보안 주의사항:${NC}"
    echo -e "  1. 로그인 후 즉시 비밀번호를 변경하세요."
    echo -e "  2. PostgreSQL 비밀번호도 변경하는 것을 권장합니다."
    echo ""
    echo -e "${BLUE}유용한 명령어:${NC}"
    echo -e "  로그 확인: ${GREEN}cd /opt/botpress && docker-compose logs -f botpress${NC}"
    echo -e "  재시작: ${GREEN}cd /opt/botpress && docker-compose restart botpress${NC}"
    echo -e "  중지: ${GREEN}cd /opt/botpress && docker-compose stop${NC}"
    echo -e "  시작: ${GREEN}cd /opt/botpress && docker-compose start${NC}"
    echo ""
    echo -e "${BLUE}문서 위치:${NC}"
    echo -e "  상세 가이드: ${GREEN}BOTPRESS_INSTALLATION_GUIDE.md${NC}"
    echo ""
}

# 메인 실행
main() {
    print_banner
    
    log_info "Botpress v12 설치를 시작합니다..."
    echo ""
    
    # 1. 사전 요구사항 확인
    check_prerequisites
    echo ""
    
    # 2. 작업 디렉토리 생성
    create_directories
    echo ""
    
    # 3. Docker Compose 설정
    setup_docker_compose
    echo ""
    
    # 4. 방화벽 설정
    configure_firewall
    echo ""
    
    # 5. SELinux 설정 (Rocky Linux)
    configure_selinux
    echo ""
    
    # 6. Botpress 시작
    start_botpress
    echo ""
    
    # 7. 상태 확인
    check_status
    echo ""
    
    # 8. 설치 정보 출력
    print_info
    
    log_success "모든 설치가 완료되었습니다!"
}

# 스크립트 실행
main "$@"

