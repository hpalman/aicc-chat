###############################################################################
# Botpress v12 자동 설치 스크립트 (PowerShell)
# 대상 서버: 192.168.133.132 (원격 SSH 접속용)
# 작성일: 2024-12-22
###############################################################################

# 오류 발생 시 중단
$ErrorActionPreference = "Stop"

# 색상 정의
function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor Blue
}

function Write-Success {
    param([string]$Message)
    Write-Host "[SUCCESS] $Message" -ForegroundColor Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "[WARNING] $Message" -ForegroundColor Yellow
}

function Write-Error-Custom {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

# 배너 출력
function Print-Banner {
    Write-Host ""
    Write-Host "╔═══════════════════════════════════════════════════════════╗" -ForegroundColor Blue
    Write-Host "║                                                           ║" -ForegroundColor Blue
    Write-Host "║        Botpress v12 원격 설치 스크립트                    ║" -ForegroundColor Blue
    Write-Host "║        Target: 192.168.133.132                            ║" -ForegroundColor Blue
    Write-Host "║                                                           ║" -ForegroundColor Blue
    Write-Host "╚═══════════════════════════════════════════════════════════╝" -ForegroundColor Blue
    Write-Host ""
}

# SSH 연결 확인
function Test-SSHConnection {
    param(
        [string]$ServerIP = "192.168.133.132",
        [string]$Username
    )
    
    Write-Info "SSH 연결 테스트 중: $Username@$ServerIP"
    
    try {
        $result = ssh -o ConnectTimeout=5 "$Username@$ServerIP" "echo 'Connection successful'"
        if ($result -eq "Connection successful") {
            Write-Success "SSH 연결 성공"
            return $true
        }
    }
    catch {
        Write-Error-Custom "SSH 연결 실패: $_"
        return $false
    }
    
    return $false
}

# 원격 서버에 파일 복사
function Copy-FilesToServer {
    param(
        [string]$ServerIP = "192.168.133.132",
        [string]$Username
    )
    
    Write-Info "파일을 원격 서버로 복사 중..."
    
    $files = @(
        "docker-compose.botpress.yml",
        "setup-botpress.sh",
        "BOTPRESS_INSTALLATION_GUIDE.md"
    )
    
    foreach ($file in $files) {
        if (Test-Path $file) {
            Write-Info "복사 중: $file"
            scp $file "${Username}@${ServerIP}:/tmp/"
            Write-Success "$file 복사 완료"
        }
        else {
            Write-Warning "$file 파일을 찾을 수 없습니다."
        }
    }
}

# 원격 서버에서 설치 스크립트 실행
function Invoke-RemoteInstallation {
    param(
        [string]$ServerIP = "192.168.133.132",
        [string]$Username
    )
    
    Write-Info "원격 서버에서 설치 시작..."
    
    $commands = @"
# 작업 디렉토리 생성
sudo mkdir -p /opt/botpress
sudo chown -R $Username:$Username /opt/botpress

# 파일 이동
mv /tmp/docker-compose.botpress.yml /opt/botpress/docker-compose.yml
mv /tmp/setup-botpress.sh /opt/botpress/
mv /tmp/BOTPRESS_INSTALLATION_GUIDE.md /opt/botpress/

# 스크립트 실행 권한 부여
chmod +x /opt/botpress/setup-botpress.sh

# 설치 스크립트 실행
cd /opt/botpress
./setup-botpress.sh
"@
    
    Write-Info "원격 명령 실행 중..."
    ssh "$Username@$ServerIP" $commands
}

# 수동 설치 가이드 출력
function Show-ManualInstructions {
    param(
        [string]$ServerIP = "192.168.133.132",
        [string]$Username
    )
    
    Write-Host ""
    Write-Host "╔═══════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
    Write-Host "║                                                           ║" -ForegroundColor Cyan
    Write-Host "║              수동 설치 가이드                             ║" -ForegroundColor Cyan
    Write-Host "║                                                           ║" -ForegroundColor Cyan
    Write-Host "╚═══════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
    Write-Host ""
    
    Write-Host "1. 서버에 SSH 접속:" -ForegroundColor Yellow
    Write-Host "   ssh $Username@$ServerIP" -ForegroundColor White
    Write-Host ""
    
    Write-Host "2. 작업 디렉토리 생성:" -ForegroundColor Yellow
    Write-Host "   sudo mkdir -p /opt/botpress" -ForegroundColor White
    Write-Host "   cd /opt/botpress" -ForegroundColor White
    Write-Host ""
    
    Write-Host "3. Docker Compose 파일 생성:" -ForegroundColor Yellow
    Write-Host "   nano docker-compose.yml" -ForegroundColor White
    Write-Host "   (docker-compose.botpress.yml 내용을 복사하여 붙여넣기)" -ForegroundColor Gray
    Write-Host ""
    
    Write-Host "4. Botpress 시작:" -ForegroundColor Yellow
    Write-Host "   docker-compose up -d" -ForegroundColor White
    Write-Host ""
    
    Write-Host "5. 로그 확인:" -ForegroundColor Yellow
    Write-Host "   docker-compose logs -f botpress" -ForegroundColor White
    Write-Host ""
    
    Write-Host "6. 웹 브라우저로 접속:" -ForegroundColor Yellow
    Write-Host "   http://$ServerIP:3000" -ForegroundColor Green
    Write-Host ""
    
    Write-Host "초기 로그인 정보:" -ForegroundColor Cyan
    Write-Host "  이메일: admin@botpress.local" -ForegroundColor White
    Write-Host "  비밀번호: Admin@2024!" -ForegroundColor White
    Write-Host ""
}

# 로컬 Docker Desktop 설치 (Windows용)
function Install-LocalBotpress {
    Write-Info "로컬 Windows 환경에서 Botpress 설치를 시작합니다..."
    
    # Docker Desktop 확인
    $dockerPath = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $dockerPath) {
        Write-Error-Custom "Docker Desktop이 설치되어 있지 않습니다."
        Write-Info "Docker Desktop을 다운로드하세요: https://www.docker.com/products/docker-desktop"
        return
    }
    
    Write-Success "Docker 설치 확인: $(docker --version)"
    
    # 현재 디렉토리에서 실행
    if (Test-Path "docker-compose.botpress.yml") {
        Write-Info "Docker Compose로 Botpress 시작 중..."
        
        # 로컬 IP로 변경
        $localIP = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object {$_.InterfaceAlias -notlike "*Loopback*"} | Select-Object -First 1).IPAddress
        Write-Info "로컬 IP 주소: $localIP"
        
        # docker-compose 실행
        docker-compose -f docker-compose.botpress.yml up -d
        
        Write-Success "Botpress 시작 완료!"
        Write-Host ""
        Write-Host "접속 URL: http://localhost:3000" -ForegroundColor Green
        Write-Host "또는: http://${localIP}:3000" -ForegroundColor Green
        Write-Host ""
        Write-Host "초기 로그인 정보:" -ForegroundColor Cyan
        Write-Host "  이메일: admin@botpress.local" -ForegroundColor White
        Write-Host "  비밀번호: Admin@2024!" -ForegroundColor White
    }
    else {
        Write-Error-Custom "docker-compose.botpress.yml 파일을 찾을 수 없습니다."
    }
}

# 메인 메뉴
function Show-Menu {
    Write-Host ""
    Write-Host "설치 옵션을 선택하세요:" -ForegroundColor Cyan
    Write-Host "  1. 원격 서버 (192.168.133.132)에 자동 설치" -ForegroundColor White
    Write-Host "  2. 수동 설치 가이드 보기" -ForegroundColor White
    Write-Host "  3. 로컬 Windows 환경에 설치" -ForegroundColor White
    Write-Host "  4. 종료" -ForegroundColor White
    Write-Host ""
    
    $choice = Read-Host "선택 (1-4)"
    return $choice
}

# 메인 실행
function Main {
    Print-Banner
    
    $choice = Show-Menu
    
    switch ($choice) {
        "1" {
            Write-Host ""
            $username = Read-Host "서버 사용자명을 입력하세요"
            $serverIP = "192.168.133.132"
            
            # SSH 연결 테스트
            if (Test-SSHConnection -ServerIP $serverIP -Username $username) {
                # 파일 복사
                Copy-FilesToServer -ServerIP $serverIP -Username $username
                
                # 원격 설치 실행
                $confirm = Read-Host "원격 서버에서 설치를 시작하시겠습니까? (y/n)"
                if ($confirm -eq "y" -or $confirm -eq "Y") {
                    Invoke-RemoteInstallation -ServerIP $serverIP -Username $username
                    Write-Success "설치가 완료되었습니다!"
                    Write-Host ""
                    Write-Host "접속 URL: http://$serverIP:3000" -ForegroundColor Green
                }
            }
            else {
                Write-Error-Custom "SSH 연결에 실패했습니다. 수동 설치 가이드를 참조하세요."
                Show-ManualInstructions -ServerIP $serverIP -Username $username
            }
        }
        "2" {
            $username = Read-Host "서버 사용자명을 입력하세요"
            Show-ManualInstructions -ServerIP "192.168.133.132" -Username $username
        }
        "3" {
            Install-LocalBotpress
        }
        "4" {
            Write-Info "종료합니다."
            exit 0
        }
        default {
            Write-Warning "잘못된 선택입니다."
            Main
        }
    }
}

# 스크립트 실행
try {
    Main
}
catch {
    Write-Error-Custom "오류 발생: $_"
    Write-Host ""
    Write-Host "문제가 지속되면 BOTPRESS_INSTALLATION_GUIDE.md를 참조하세요." -ForegroundColor Yellow
}

