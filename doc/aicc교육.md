(허) 허팔만 메모 추가됨 - 2026.1.13(화)

# aicc 환경구성

## 로컬환경 구성
	1. aicc, aicc-admin은 별도의 cursor로 생성하세요 (꼭은 아님)
	- gradle version이 다름
	- aicc-admin: 8.10
	- aicc: 7.15

	2. jdk 필요
	- openJdk-17, jdk 1.8 
	- aicc-admin: openJdk-17
	- aicc: jdk 1.8

	3. node nvm을 설치합니다.
	- node 16 and 22를 사용합니다.
	- aicc-admin: 16
	- aicc: 22 
	
## gitLab에서 소스 clone 후 frontend lib 다운로드(install)
	1. aicc-frontend, aicc-backend, aicc-admin-frontend, aicc-admin-backend
	2. aicc-frontend
		- package.json파일에 node 버전이 너무 낮게 설정되었다면 20이상으로 사용할 수 있도록 변경
		- ```json
		  "engines": {
			"node": ">20.x"
		  },
		```
		- >yarn install   [ (허) --network-timeout 600000] # 10분 타임아웃
	
	3. aicc-admin-frontend
		- >npm install
	
## 구동
	1. frontend, backend의 실행방법이 다름
  ■ 2. aicc-backend ( aicc / aicc ▶ http://10.50.1.23:8082/aicc/aicc.git )
		- gradlew bootRun -x test
        (허) E:\aicc-dev\aicc\workspace\aicc>gradlew bootRun -x test , bash에서 ./gradlew bootRun -x test 성공
		- TOOL> SPRING:APPS > project선택한 후 `>`버튼 클릭

  ■ 3. aicc-admin-backend
		- gradlew bootRun -x test [ ■ (허) git bash에서 ./gradlew bootRun -x test 성공. 한글은 깨짐 상태. 폰트를 D2Coding,굴림체, UTF-8이나 Default로 해도 깨짐 ]
		- TOOL> SPRING:APPS > project선택한 후 `>`버튼 클릭
	    (허) http://localhost:8080 ?
        
  ■ 4. aicc-frontend ( aicc / Aicc Frontend ▶ http://10.50.1.23:8082/aicc/aicc-frontend.git )
        cd /e/aicc-dev/aicc/workspace/aicc-frontend
        (허) $ nvm use 22
             $ yarn install --network-timeout 600000 --verbose # AICESS VPN 끄고 저녁 6시 이후에 시도하여 성공함. 22에서만 yarn 명령이 작동됨.
		- node 22 사용하므로 `nvm use 22`
		- aicc-frontend는 next.js로 개발됨
        (허) vpn 연결 후, $ yarn dev # BASH에서 실행후 Compiled 다 되면, http://localhost 브라우저에서 접속. 접속해야 Compiling 함. Next.js 14.2.35를 사용한다고 나오며 http://localhost:80로 접속하라고 나옴.
             권이사가 와츠앱에서 보내온 .env.local 파일을 "E:\aicc-dev\aicc\workspace\aicc-frontend\.env.local" 에 복사하면 실행중이면 다시 로드함
             Sign in | Jwt - AICC
		- >yarn dev --> 로컬 node에서 프로그램 실행 시작
        주) THYMELEAF로 CHAT 기능이 데모버전으로 있는 것 같다 함

  ■ 5. aicc-admin-frontend [ (허) ■ aicc / aicc-admin-frontend ▶ http://10.50.1.23:8082/aicc/aicc-admin-frontend.git ]
          (허) SMARTCC - 에이아이세스
		- node 16을 사용하므로 `nvm use 16`
          (허) bash에서 작업
		- aicc-admin-frontend는 ReactJs로 개발됨
          (허) npm install (처음엔 필요함)
		- >npm start dev --> 로컬 node에서 프로그램 실행 시작
          (허) http://localhost:3000 성공(로그인 ID 모름)

SSE
	
■ aicc / aicc-chat [ (허) ■ AICC 고객 채팅, AICC 상담원 콘솔]
  git clone http://10.50.1.23:8082/aicc/aicc-chat.git [palman/Ckawhgdk@!12]
  (허)
  Bash > ./gradlew bootRun -x test
  REDIS, RABBITMQ
  DB는 안쓴다 함
    Spring Boot (v3.4.1) java ? "OpenJDK17U-jdk_x64_windows_hotspot_17.0.15_6.msi" 이건가?
    명령프롬프트> set JAVA_HOME=... PATH=%JAVA_HOME%\bin;%PATH%
    
    http://localhost:28070/frontend/websocket-client.html (o)
    http://localhost:28070/frontend/admin-client.html (o)

aicc / Customer-Chat
    customer-chat
    .env 파일 추가
    http://10.50.1.23:8082/aicc/customer-chat.git
    THYMELEAF -> NEXT JS로 변경만 해놓은 것. 랜더링만 한다.
    허) npm i
        npm run dev
        Next.js 14.2.22
        http://localhost:8081
        nvm install 20
        Downloading node.js version 20.20.0 (64-bit)
        
        $ nvm -v
        1.2.2
        $ nvm use 20
        Now using node v20.20.0 (64-bit)
        $ corepack enable
        $ corepack prepare yarn@stable --activate
        $ yarn -v
        1.22.22
        $ yarn dev

        http://localhost:8081/apt001/
        아이디 > 진테스트
        전화번호 > 01012341234
        비밀번호 > 1234 입니다!

aicc / Customer-Chat-Backend
  http://10.50.1.23:8082/aicc/customer-chat-backend.git
  E:\aicc-dev\aicc\workspace\customer-chat-backend
  허)
  Unsupported class file major version 65
  $ JAVA_HOME=/C/app/java/jdk-17.0.15.6-hotspot
  $ export PATH=$JAVA_HOME/bin:$PATH
  $ ./gradlew bootRun -x test
442 port 
  Spring Boot 2.7.18
  DB > postgresql://10.50.1.58:5432/aicc
  SSE
  
  
  
Could not run phased build action using connection to Gradle distribution 'https://services.gradle.org/distributions/gradle-8.10-bin.zip'.
Could not write cache value to 'C:\Users\Administrator\.gradle\daemon\8.10\registry.bin'.
디스크 공간이 부족합니다