# Spring Boot Dashboard 설정 가이드

> **작성일**: 2026-01-23  
> **목적**: Cursor/VS Code에서 Spring Boot Dashboard에 AICC-CHAT 표시  
> **생성 파일**: `.vscode/settings.json`, `launch.json`, `tasks.json`, `extensions.json`

---

## 📋 목차

1. [문제 상황](#-문제-상황)
2. [생성된 파일](#-생성된-파일)
3. [필수 Extension](#-필수-extension)
4. [설정 적용 방법](#-설정-적용-방법)
5. [Spring Boot Dashboard 사용법](#-spring-boot-dashboard-사용법)
6. [문제 해결](#-문제-해결)

---

## 🚨 문제 상황

### 증상
- Cursor 좌측 패널의 **Spring Boot Dashboard**에 AICC-CHAT 프로젝트가 표시되지 않음
- "APPS" 섹션이 비어있거나 다른 프로젝트만 표시됨

### 원인
1. `.vscode` 폴더 및 설정 파일 부재
2. Spring Boot Extension 미설치
3. Java/Gradle 인식 문제

---

## 📁 생성된 파일

### 1. `.vscode/settings.json` - 기본 설정

```json
{
    // Java 홈 디렉토리 설정
    "java.configuration.runtimes": [
        {
            "name": "JavaSE-17",
            "path": "C:\\Program Files\\Java\\jdk-17",
            "default": true
        }
    ],
    
    // Spring Boot 설정
    "spring-boot.ls.java.home": "C:\\Program Files\\Java\\jdk-17",
    "spring-boot.ls.problem.application-properties.enabled": true,
    
    // Gradle 자동 빌드
    "java.configuration.updateBuildConfiguration": "automatic",
    "java.import.gradle.enabled": true,
    "java.import.gradle.wrapper.enabled": true
}
```

**주요 설정:**
- Java 17 경로 지정
- Spring Boot Language Server 활성화
- Gradle 자동 import 활성화
- UTF-8 인코딩 설정

---

### 2. `.vscode/launch.json` - 실행 설정

```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Spring Boot-AiccChatApplication",
            "request": "launch",
            "cwd": "${workspaceFolder}",
            "mainClass": "aicc.AiccChatApplication",
            "projectName": "aicc-chat",
            "vmArgs": "-Dfile.encoding=UTF-8"
        }
    ]
}
```

**기능:**
- Spring Boot 애플리케이션 실행 구성
- UTF-8 인코딩으로 실행
- 디버그 모드 지원

---

### 3. `.vscode/tasks.json` - Gradle 작업

```json
{
    "version": "2.0.0",
    "tasks": [
        {
            "label": "gradle: bootRun",
            "type": "shell",
            "command": ".\\gradlew.bat",
            "args": ["bootRun"],
            "group": {
                "kind": "build",
                "isDefault": true
            }
        }
    ]
}
```

**포함된 작업:**
- `gradle: bootRun` - Spring Boot 실행
- `gradle: build` - 프로젝트 빌드
- `gradle: clean` - 빌드 정리
- `gradle: compileJava` - Java 컴파일

---

### 4. `.vscode/extensions.json` - 권장 Extension

```json
{
    "recommendations": [
        "vmware.vscode-boot-dev-pack",
        "vmware.vscode-spring-boot",
        "vscjava.vscode-java-pack",
        "vscjava.vscode-spring-initializr",
        "vscjava.vscode-spring-boot-dashboard",
        "vscjava.vscode-gradle",
        "gabrielbb.vscode-lombok",
        "redhat.java"
    ]
}
```

**권장 Extension:**
- **Spring Boot Extension Pack** (필수)
- **Spring Boot Dashboard** (필수)
- **Java Extension Pack** (필수)
- **Gradle for Java**
- **Lombok Annotations Support**

---

## 🔌 필수 Extension

### 1. Spring Boot Extension Pack 설치

**방법 1: Cursor UI에서 설치**
```
1. Ctrl+Shift+X (Extensions 패널)
2. "Spring Boot Extension Pack" 검색
3. Install 클릭
```

**방법 2: 명령 팔레트에서 설치**
```
1. Ctrl+Shift+P
2. "Extensions: Install Extensions" 입력
3. "Spring Boot Extension Pack" 검색 및 설치
```

**포함 Extension:**
- Spring Boot Tools
- Spring Initializr Java Support
- Spring Boot Dashboard
- Spring Boot Support for VS Code

---

### 2. Java Extension Pack 설치

**필수 Extension:**
- Language Support for Java(TM) by Red Hat
- Debugger for Java
- Test Runner for Java
- Maven for Java
- Project Manager for Java
- Visual Studio IntelliCode

---

### 3. Gradle Extension 설치

```
Extension ID: vscjava.vscode-gradle
```

---

## ⚙️ 설정 적용 방법

### 1단계: Extension 설치 확인

```
1. Ctrl+Shift+X (Extensions 패널)
2. 설치된 Extension 확인:
   - Spring Boot Extension Pack ✅
   - Java Extension Pack ✅
   - Gradle for Java ✅
```

---

### 2단계: Java 경로 확인 및 수정

**Java 설치 경로 확인:**
```powershell
# PowerShell에서 실행
where java
java -version
```

**경로가 다르면 `.vscode/settings.json` 수정:**
```json
{
    "java.configuration.runtimes": [
        {
            "name": "JavaSE-17",
            "path": "실제_Java_경로", // 예: C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.5.8-hotspot
            "default": true
        }
    ]
}
```

---

### 3단계: Gradle 프로젝트 새로고침

**방법 1: 명령 팔레트**
```
Ctrl+Shift+P → "Java: Clean Java Language Server Workspace"
Ctrl+Shift+P → "Java: Reload Projects"
```

**방법 2: 터미널**
```powershell
.\gradlew clean build --refresh-dependencies
```

---

### 4단계: Cursor 재시작

```
1. Ctrl+Shift+P
2. "Developer: Reload Window" 입력 및 실행
또는
Cursor 완전 종료 후 재시작
```

---

### 5단계: Spring Boot Dashboard 확인

```
1. 좌측 패널에서 Spring 아이콘 클릭 (스프링 잎사귀 모양)
2. "APPS" 섹션 확인
3. "aicc-chat" 또는 "AiccChatApplication" 표시 확인
```

---

## 🚀 Spring Boot Dashboard 사용법

### 1. 애플리케이션 실행

**방법 1: Dashboard에서 실행**
```
1. Spring Boot Dashboard 열기
2. "aicc-chat" 항목 찾기
3. ▶️ 재생 버튼 클릭
```

**방법 2: 우클릭 메뉴**
```
1. "aicc-chat" 우클릭
2. "Run" 또는 "Debug" 선택
```

---

### 2. 로그 확인

```
1. 실행 중인 앱 옆의 로그 아이콘 클릭
또는
2. 터미널 패널에서 자동으로 로그 표시
```

---

### 3. 애플리케이션 중지

```
1. 실행 중인 앱 찾기
2. ⏹️ 정지 버튼 클릭
또는
Ctrl+C (터미널에서)
```

---

### 4. 디버그 모드

```
1. "aicc-chat" 우클릭
2. "Debug" 선택
3. 브레이크포인트 설정 가능
```

---

## 🐛 문제 해결

### 문제 1: AICC-CHAT이 여전히 안 보임

**해결책:**

1. **Java Language Server 재시작**
   ```
   Ctrl+Shift+P → "Java: Clean Java Language Server Workspace"
   → "Reload and delete" 선택
   ```

2. **Gradle 캐시 삭제**
   ```powershell
   .\gradlew clean
   .\gradlew build --refresh-dependencies
   ```

3. **Cursor 완전 재시작**
   ```
   작업 관리자에서 Cursor 프로세스 모두 종료 후 재시작
   ```

---

### 문제 2: "No Spring Boot projects found"

**원인:**
- `@SpringBootApplication` 어노테이션 없음
- `main` 메서드 없음
- Gradle 빌드 실패

**확인:**
```java
// src/main/java/aicc/AiccChatApplication.java
@SpringBootApplication
public class AiccChatApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiccChatApplication.class, args);
    }
}
```

**빌드 확인:**
```powershell
.\gradlew compileJava
```

---

### 문제 3: Java 경로 오류

**증상:**
```
"Java runtime could not be located"
```

**해결:**

1. **Java 설치 확인**
   ```powershell
   java -version
   # 출력: openjdk version "17.0.x"
   ```

2. **JAVA_HOME 환경변수 설정**
   ```powershell
   # PowerShell (관리자 권한)
   [System.Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\Program Files\Java\jdk-17', 'Machine')
   ```

3. **settings.json 경로 수정**
   ```json
   {
       "java.configuration.runtimes": [
           {
               "path": "실제_Java_설치_경로"
           }
       ]
   }
   ```

---

### 문제 4: Gradle Wrapper 오류

**증상:**
```
"Could not find or load main class org.gradle.wrapper.GradleWrapperMain"
```

**해결:**
```powershell
# Gradle Wrapper 재생성
gradle wrapper
```

---

### 문제 5: Extension 설치 후에도 안 보임

**해결:**

1. **Extension 활성화 확인**
   ```
   Extensions 패널 → "Spring Boot Extension Pack" → Enabled 확인
   ```

2. **Extension 재설치**
   ```
   1. "Spring Boot Extension Pack" 검색
   2. Uninstall 클릭
   3. Reload Window
   4. 다시 Install
   ```

3. **Workspace Trust 확인**
   ```
   Ctrl+Shift+P → "Workspaces: Manage Workspace Trust"
   → "Trust Workspace" 선택
   ```

---

## ✅ 확인 체크리스트

### 설정 완료 확인

- [ ] `.vscode` 폴더 존재
- [ ] `.vscode/settings.json` 생성됨
- [ ] `.vscode/launch.json` 생성됨
- [ ] `.vscode/tasks.json` 생성됨
- [ ] `.vscode/extensions.json` 생성됨

### Extension 설치 확인

- [ ] Spring Boot Extension Pack 설치됨
- [ ] Java Extension Pack 설치됨
- [ ] Gradle for Java 설치됨
- [ ] Lombok Annotations Support 설치됨

### Java 환경 확인

- [ ] Java 17 설치됨
- [ ] JAVA_HOME 환경변수 설정됨
- [ ] `java -version` 명령 정상 동작

### Gradle 확인

- [ ] `.\gradlew.bat` 파일 존재
- [ ] `.\gradlew build` 성공
- [ ] `build.gradle` 정상

### Spring Boot Dashboard 확인

- [ ] 좌측 패널에 Spring 아이콘 표시
- [ ] "APPS" 섹션에 "aicc-chat" 표시
- [ ] 앱 실행 가능 (▶️ 버튼)

---

## 🎯 최종 확인

### Spring Boot Dashboard 정상 작동 확인

```
1. Cursor 좌측 패널 Spring 아이콘 클릭
   ↓
2. "APPS" 섹션 펼치기
   ↓
3. "aicc-chat" 또는 "AiccChatApplication" 확인
   ↓
4. ▶️ 버튼 클릭하여 실행
   ↓
5. 터미널에서 Spring Boot 로그 확인
   ↓
6. "Started AiccChatApplication in X seconds" 메시지 확인
```

**성공 로그 예시:**
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.4.1)

2026-01-23 16:00:00 INFO  AiccChatApplication - Started AiccChatApplication in 5.234 seconds
```

---

## 📝 추가 팁

### 1. 여러 프로젝트 동시 실행

Spring Boot Dashboard에서 여러 앱을 동시에 실행할 수 있습니다:
```
1. 첫 번째 앱 실행
2. 두 번째 앱 실행 (다른 포트 사용)
3. Dashboard에서 모두 관리
```

---

### 2. 커스텀 VM Arguments

`launch.json`에서 VM 옵션 추가:
```json
{
    "vmArgs": "-Dfile.encoding=UTF-8 -Dspring.profiles.active=dev -Xmx512m"
}
```

---

### 3. 환경 변수 설정

`.env` 파일 생성 (프로젝트 루트):
```env
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=28070
REDIS_HOST=localhost
REDIS_PORT=6379
```

---

### 4. 프로파일별 실행

`launch.json`에 프로파일 추가:
```json
{
    "configurations": [
        {
            "name": "Spring Boot-AiccChatApplication (dev)",
            "vmArgs": "-Dspring.profiles.active=dev"
        },
        {
            "name": "Spring Boot-AiccChatApplication (prod)",
            "vmArgs": "-Dspring.profiles.active=prod"
        }
    ]
}
```

---

## 🎉 완료!

Spring Boot Dashboard가 정상적으로 작동하면 AICC-CHAT 프로젝트를 쉽게 실행/중지/디버그할 수 있습니다!

**주요 이점:**
- ✅ 클릭 한 번으로 앱 실행/중지
- ✅ 실시간 로그 모니터링
- ✅ 디버그 모드 전환 용이
- ✅ 여러 앱 동시 관리
- ✅ 빠른 재시작

---

**작성**: AI Assistant  
**문서 버전**: 1.0  
**최종 수정**: 2026-01-23
