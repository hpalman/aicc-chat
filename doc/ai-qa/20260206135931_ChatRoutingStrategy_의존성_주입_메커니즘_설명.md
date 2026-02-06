# ChatRoutingStrategy 의존성 주입 메커니즘 설명

> **작성일**: 2026-02-06  
> **목적**: `ChatAgentService`에서 `routingStrategy`가 어떻게 `DynamicRoutingStrategy`를 가리키게 되는지 설명  
> **범위**: Spring 의존성 주입, 조건부 Bean 생성, 전략 패턴

---

## 📋 목차

1. [개요](#개요)
2. [의존성 주입 흐름](#의존성-주입-흐름)
3. [조건부 Bean 생성](#조건부-bean-생성)
4. [전체 구조도](#전체-구조도)
5. [코드 분석](#코드-분석)
6. [동작 원리](#동작-원리)

---

## 개요

`ChatAgentService`에서 `routingStrategy.handleMessage()`를 호출할 때, 실제로는 `DynamicRoutingStrategy`의 메서드가 실행됩니다. 이는 **Spring의 의존성 주입(Dependency Injection)**과 **조건부 Bean 생성** 메커니즘을 통해 이루어집니다.

### 핵심 개념

1. **인터페이스 기반 프로그래밍**: `ChatRoutingStrategy` 인터페이스 사용
2. **전략 패턴**: 여러 구현체 중 하나를 선택하여 사용
3. **조건부 Bean 생성**: `@ConditionalOnProperty`로 설정에 따라 Bean 선택
4. **의존성 주입**: Spring이 자동으로 적절한 구현체를 주입

---

## 의존성 주입 흐름

### 1. ChatAgentService에서 선언

**파일**: `src/main/java/aicc/chat/service/ChatAgentService.java`

```java
@Service
@RequiredArgsConstructor
public class ChatAgentService extends ChatService {
    // ...
    private final ChatRoutingStrategy routingStrategy;  // ✅ 인터페이스 타입으로 선언
    // ...
    
    public void agentMessage(ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
        // ...
        routingStrategy.handleMessage(roomId, message); // DynamicRoutingStrategy 호출
        // ...
    }
}
```

**핵심 포인트**:
- `ChatRoutingStrategy`는 **인터페이스**입니다
- `@RequiredArgsConstructor`로 생성자 주입 사용
- Spring이 런타임에 적절한 구현체를 주입합니다

### 2. Spring 컨테이너 시작 시 Bean 생성

**파일**: `src/main/java/aicc/chat/config/ChatRoutingConfig.java`

```java
@Configuration
public class ChatRoutingConfig {
    
    @Bean
    @ConditionalOnProperty(name = "app.chat.mode", havingValue = "HYBRID", matchIfMissing = true)
    public ChatRoutingStrategy dynamicRoutingStrategy(...) {
        // DynamicRoutingStrategy 인스턴스 생성 및 반환
        return new DynamicRoutingStrategy(roomRepository, miChat, agent, roomUpdateBroadcaster);
    }
    
    @Bean
    @ConditionalOnProperty(name = "app.chat.mode", havingValue = "MICHAT")
    public ChatRoutingStrategy miChatRoutingStrategy(...) {
        // MiChatRoutingStrategy 인스턴스 생성 및 반환
        return new MiChatRoutingStrategy(...);
    }
    
    @Bean
    @ConditionalOnProperty(name = "app.chat.mode", havingValue = "AGENT")
    public ChatRoutingStrategy agentRoutingStrategy(...) {
        // AgentRoutingStrategy 인스턴스 생성 및 반환
        return new AgentRoutingStrategy(messageBroker);
    }
}
```

**핵심 포인트**:
- 세 개의 `@Bean` 메서드가 모두 `ChatRoutingStrategy` 타입을 반환
- `@ConditionalOnProperty`로 **조건부로 하나만 활성화**됨
- `matchIfMissing = true`로 기본값은 `HYBRID`

### 3. application.yml 설정

**파일**: `src/main/resources/application.yml`

```yaml
app:
  chat:
    mode: HYBRID      # MiChat -> Agent 전환 모드 (기본값)
    # mode: MICHAT    # MiChat(자체 AI 엔진) 연동 모드
    # mode: AGENT     # 상담원 전용 모드 (봇 없음)
```

**핵심 포인트**:
- `app.chat.mode = HYBRID` (또는 설정 없음)
- `@ConditionalOnProperty(name = "app.chat.mode", havingValue = "HYBRID", matchIfMissing = true)` 조건 만족
- `dynamicRoutingStrategy()` Bean만 생성됨

---

## 조건부 Bean 생성

### @ConditionalOnProperty 동작 원리

```java
@ConditionalOnProperty(
    name = "app.chat.mode",           // 설정 키
    havingValue = "HYBRID",           // 값이 "HYBRID"일 때 활성화
    matchIfMissing = true             // 설정이 없어도 활성화 (기본값)
)
```

### Bean 생성 시나리오

| application.yml 설정 | 활성화되는 Bean | 결과 |
|---------------------|----------------|------|
| `app.chat.mode: HYBRID` 또는 설정 없음 | `dynamicRoutingStrategy()` | `DynamicRoutingStrategy` 인스턴스 생성 |
| `app.chat.mode: MICHAT` | `miChatRoutingStrategy()` | `MiChatRoutingStrategy` 인스턴스 생성 |
| `app.chat.mode: AGENT` | `agentRoutingStrategy()` | `AgentRoutingStrategy` 인스턴스 생성 |

### 중요한 점

- **동시에 여러 Bean이 생성되지 않음**: 조건이 충돌하지 않도록 설계됨
- **하나의 Bean만 Spring 컨테이너에 등록됨**: `ChatRoutingStrategy` 타입의 Bean은 하나만 존재
- **Spring이 자동으로 주입**: `ChatAgentService`에 주입할 때 유일한 Bean을 찾아서 주입

---

## 전체 구조도

### Spring 컨테이너 초기화 과정

```
1. Spring Boot 애플리케이션 시작
   ↓
2. @Configuration 클래스 스캔
   ↓
3. ChatRoutingConfig 발견
   ↓
4. application.yml에서 app.chat.mode 읽기
   ↓
5. 조건 확인:
   ├─ app.chat.mode == "HYBRID" 또는 없음?
   │   └─ YES → dynamicRoutingStrategy() Bean 생성
   │            → DynamicRoutingStrategy 인스턴스 생성
   │            → Spring 컨테이너에 등록 (이름: "dynamicRoutingStrategy", 타입: ChatRoutingStrategy)
   │
   ├─ app.chat.mode == "MICHAT"?
   │   └─ YES → miChatRoutingStrategy() Bean 생성
   │
   └─ app.chat.mode == "AGENT"?
       └─ YES → agentRoutingStrategy() Bean 생성
   ↓
6. ChatAgentService Bean 생성 시도
   ↓
7. 생성자에 ChatRoutingStrategy 타입 파라미터 발견
   ↓
8. Spring 컨테이너에서 ChatRoutingStrategy 타입의 Bean 검색
   ↓
9. "dynamicRoutingStrategy" Bean 발견 (DynamicRoutingStrategy 인스턴스)
   ↓
10. ChatAgentService 생성자에 주입
    ↓
11. ChatAgentService.routingStrategy 필드 = DynamicRoutingStrategy 인스턴스
```

### 런타임 호출 흐름

```
ChatAgentService.agentMessage()
   ↓
routingStrategy.handleMessage(roomId, message)
   ↓
[실제로는]
DynamicRoutingStrategy.handleMessage(roomId, message)
   ↓
방 상태 확인 (BOT/WAITING/AGENT)
   ↓
적절한 전략으로 위임:
   ├─ BOT → miChatRoutingStrategy.handleMessage()
   ├─ WAITING → agentRoutingStrategy.handleMessage()
   └─ AGENT → agentRoutingStrategy.handleMessage()
```

---

## 코드 분석

### 1. 인터페이스 정의

**파일**: `src/main/java/aicc/chat/service/inteface/ChatRoutingStrategy.java`

```java
public interface ChatRoutingStrategy {
    void handleMessage(String roomId, ChatMessage message);
    default void onRoomCreated(ChatRoom room) {
        // 기본 구현
    }
}
```

**역할**: 모든 라우팅 전략이 구현해야 하는 계약 정의

### 2. 구현체들

#### DynamicRoutingStrategy

```java
public class DynamicRoutingStrategy implements ChatRoutingStrategy {
    private final RoomRepository roomRepository;
    private final MiChatRoutingStrategy miChatRoutingStrategy;
    private final AgentRoutingStrategy agentRoutingStrategy;
    
    @Override
    public void handleMessage(String roomId, ChatMessage message) {
        String routingMode = roomRepository.getRoutingMode(roomId);
        
        if (MODE_AGENT.equals(routingMode)) {
            agentRoutingStrategy.handleMessage(roomId, message);
        } else {
            miChatRoutingStrategy.handleMessage(roomId, message);
        }
    }
}
```

**역할**: 방 상태에 따라 적절한 전략으로 위임하는 **위임자(Delegator)**

#### MiChatRoutingStrategy

```java
public class MiChatRoutingStrategy implements ChatRoutingStrategy {
    @Override
    public void handleMessage(String roomId, ChatMessage message) {
        // MiChat AI 봇으로 메시지 전송
    }
}
```

**역할**: 챗봇 메시지 처리 전략

#### AgentRoutingStrategy

```java
public class AgentRoutingStrategy implements ChatRoutingStrategy {
    @Override
    public void handleMessage(String roomId, ChatMessage message) {
        // 상담원에게 메시지 브로드캐스트
    }
}
```

**역할**: 상담원 메시지 처리 전략

### 3. Bean 생성 설정

**파일**: `src/main/java/aicc/chat/config/ChatRoutingConfig.java`

```java
@Configuration
public class ChatRoutingConfig {
    
    @Bean
    @ConditionalOnProperty(name = "app.chat.mode", havingValue = "HYBRID", matchIfMissing = true)
    public ChatRoutingStrategy dynamicRoutingStrategy(...) {
        // MiChatRoutingStrategy와 AgentRoutingStrategy를 내부적으로 생성
        MiChatRoutingStrategy miChat = new MiChatRoutingStrategy(...);
        AgentRoutingStrategy agent = new AgentRoutingStrategy(...);
        
        // DynamicRoutingStrategy에 주입하여 반환
        return new DynamicRoutingStrategy(roomRepository, miChat, agent, roomUpdateBroadcaster);
    }
}
```

**핵심**:
- `@Bean` 메서드가 `ChatRoutingStrategy` 타입을 반환
- Spring이 이 반환값을 컨테이너에 등록
- 다른 클래스에서 `ChatRoutingStrategy` 타입을 요청하면 이 Bean이 주입됨

### 4. 의존성 주입

**파일**: `src/main/java/aicc/chat/service/ChatAgentService.java`

```java
@Service
@RequiredArgsConstructor  // Lombok이 생성자 자동 생성
public class ChatAgentService {
    private final ChatRoutingStrategy routingStrategy;  // 생성자 주입
    
    // Lombok이 생성하는 생성자 (의사 코드):
    // public ChatAgentService(..., ChatRoutingStrategy routingStrategy) {
    //     this.routingStrategy = routingStrategy;  // Spring이 주입
    // }
}
```

**동작 과정**:
1. Spring이 `ChatAgentService` Bean 생성 시도
2. 생성자에 `ChatRoutingStrategy` 타입 파라미터 발견
3. Spring 컨테이너에서 `ChatRoutingStrategy` 타입의 Bean 검색
4. `dynamicRoutingStrategy` Bean 발견 (DynamicRoutingStrategy 인스턴스)
5. 생성자에 주입
6. `routingStrategy` 필드에 `DynamicRoutingStrategy` 인스턴스 저장

---

## 동작 원리

### Spring의 타입 기반 Bean 검색

```java
// Spring 내부 동작 (의사 코드)
public <T> T getBean(Class<T> requiredType) {
    // 1. 컨테이너에서 requiredType 타입의 Bean 검색
    List<Bean> candidates = findAllBeansOfType(requiredType);
    
    // 2. Bean이 하나만 있으면 반환
    if (candidates.size() == 1) {
        return candidates.get(0).getInstance();
    }
    
    // 3. Bean이 여러 개면 예외 발생 (Ambiguous dependency)
    if (candidates.size() > 1) {
        throw new NoUniqueBeanDefinitionException(...);
    }
    
    // 4. Bean이 없으면 예외 발생
    throw new NoSuchBeanDefinitionException(...);
}
```

### 현재 프로젝트의 경우

```
ChatRoutingStrategy 타입의 Bean 검색
   ↓
발견된 Bean:
   - dynamicRoutingStrategy (DynamicRoutingStrategy 인스턴스)
   ↓
Bean이 하나만 있음 → 주입 성공
```

### 만약 조건이 충돌한다면?

만약 `application.yml`에서 다음과 같이 설정한다면:

```yaml
app:
  chat:
    mode: HYBRID  # 동시에
    mode: MICHAT  # 두 개가 활성화되면?
```

**결과**: 
- 두 개의 Bean이 생성됨
- `ChatAgentService` 생성 시 `NoUniqueBeanDefinitionException` 발생
- 애플리케이션 시작 실패

**해결책**: 
- `@Primary` 어노테이션 사용
- `@Qualifier` 어노테이션으로 특정 Bean 지정
- 조건을 명확히 분리 (현재 구현이 올바름)

---

## 실제 실행 흐름 예시

### 시나리오: 고객이 메시지를 보냄

```
1. 고객이 WebSocket으로 메시지 전송
   ↓
2. ChatCustomerController.onCustomerMessage() 호출
   ↓
3. routingStrategy.handleMessage(roomId, message) 호출
   ↓
4. [실제로는] DynamicRoutingStrategy.handleMessage() 실행
   ↓
5. roomRepository.getRoutingMode(roomId) 호출
   ↓
6. 방 상태 확인:
   ├─ "BOT" → miChatRoutingStrategy.handleMessage() 호출
   ├─ "WAITING" → agentRoutingStrategy.handleMessage() 호출
   └─ "AGENT" → agentRoutingStrategy.handleMessage() 호출
```

### 시나리오: 상담원이 메시지를 보냄

```
1. 상담원이 WebSocket으로 메시지 전송
   ↓
2. ChatAgentService.agentMessage() 호출
   ↓
3. routingStrategy.handleMessage(roomId, message) 호출
   ↓
4. [실제로는] DynamicRoutingStrategy.handleMessage() 실행
   ↓
5. message.getSenderRole() 확인
   ↓
6. UserRole.AGENT인 경우:
   → agentRoutingStrategy.handleMessage() 직접 호출
   (방 상태 확인 없이 상담원 메시지는 항상 상담원 전략으로 처리)
```

---

## 요약

### 핵심 메커니즘

1. **인터페이스 기반 설계**
   - `ChatRoutingStrategy` 인터페이스 정의
   - 여러 구현체가 동일한 인터페이스 구현

2. **조건부 Bean 생성**
   - `@ConditionalOnProperty`로 설정에 따라 Bean 선택
   - `app.chat.mode = HYBRID` → `DynamicRoutingStrategy` 생성

3. **Spring 의존성 주입**
   - 생성자 주입 (`@RequiredArgsConstructor`)
   - 타입 기반 Bean 검색
   - 유일한 Bean 자동 주입

4. **런타임 다형성**
   - 컴파일 타임에는 인터페이스 타입
   - 런타임에는 실제 구현체 인스턴스
   - 메서드 호출 시 실제 구현체의 메서드 실행

### 의존성 주입 체인

```
application.yml (app.chat.mode: HYBRID)
   ↓
@ConditionalOnProperty 조건 만족
   ↓
ChatRoutingConfig.dynamicRoutingStrategy() Bean 생성
   ↓
DynamicRoutingStrategy 인스턴스 생성
   ↓
Spring 컨테이너에 등록 (타입: ChatRoutingStrategy)
   ↓
ChatAgentService 생성자에 주입
   ↓
ChatAgentService.routingStrategy = DynamicRoutingStrategy 인스턴스
   ↓
routingStrategy.handleMessage() 호출 시
   ↓
DynamicRoutingStrategy.handleMessage() 실행
```

---

**작성**: AI Assistant  
**문서 버전**: 1.0  
**최종 수정**: 2026-02-06
