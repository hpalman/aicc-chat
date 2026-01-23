#!/bin/bash

###############################################################################
# Botpress 설정 오류 자동 수정 스크립트
# 오류: botpress.config.json not found
###############################################################################

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}╔═══════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                                                           ║${NC}"
echo -e "${BLUE}║     Botpress 설정 오류 자동 수정 스크립트                ║${NC}"
echo -e "${BLUE}║                                                           ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════════╝${NC}"
echo ""

cd /opt/botpress

echo -e "${YELLOW}[1/6]${NC} 현재 컨테이너 중지 중..."
docker compose down

echo -e "${YELLOW}[2/6]${NC} 기존 볼륨 제거 중 (데이터 초기화)..."
docker compose down -v

echo -e "${YELLOW}[3/6]${NC} Docker 이미지 다시 받기..."
docker compose pull

echo -e "${YELLOW}[4/6]${NC} Botpress 시작 중..."
docker compose up -d

echo -e "${YELLOW}[5/6]${NC} 초기화 대기 중 (60초)..."
echo -e "${BLUE}Botpress가 초기 설정 파일을 생성하고 있습니다...${NC}"
sleep 60

echo -e "${YELLOW}[6/6]${NC} 상태 확인 중..."
echo ""
docker compose ps
echo ""

echo -e "${GREEN}✓ 수정 완료!${NC}"
echo ""
echo -e "${BLUE}로그 확인:${NC}"
echo -e "  ${GREEN}docker compose logs -f botpress${NC}"
echo ""
echo -e "${BLUE}웹 접속:${NC}"
echo -e "  ${GREEN}http://192.168.133.132:3000${NC}"
echo ""

