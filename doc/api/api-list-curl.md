
# api 목차
## 상담사용 API

| api                        | 이름                                    | 설명                                      |
|----------------------------|---------------------------------------|-----------------------------------------|
| /api/agent/status          | [상담사 상태 조회](#api_agent_status)        |                                         |
| /api/agent/status/{status} | [상담사 상태 변경](#api_agent_status_status) |  |
| /api/agent/availability    | [상담사 가용성 조회](#api_agent_availability) |  |


 
## 고객용 API
- [api/customer/chat-end 고객 상담 종료](#api-customer-chat-end)

| api                        | 이름                                             | 설명                                      |
|----------------------------|------------------------------------------------|-----------------------------------------|
| /api/customer/chat-end     | [고객 상담 종료](#api-customer-chat-end) |                                         |


[내 소개로 이동](#내-소개)

☆ 룸관리 - 매우 중요 ☆
상담원연결신청
상담원연결취소

ㅁ [UI/웹소켓] 종료 > 채팅방에서 나가기 : 웹소켓 연결 종료 (★★★★★ 가장 중요함 ★★★★★)
  - stompClient > /app/customer/chat
    type: LEAVE
    stompClient.disconnect
    CustomerChatController:onCustomerMessage
        - 이력저장
        - 세션의 마지막 활동 시간 업데이트

ㅁ [UI/웹소켓] 상담시작하기 > 웹소켓 연결

6. 웹소켓연결
   ws://localhost:28070/ws-chat/638/lzplgyti/websocket?token=eyJ1c2Vy . . . DAxIn0=&roomId=room-885162ab
ws://localhost:28070/ws-chat/638/lzplgyti/websocket?token=eyJ1c2VySWQiOiJjdXN0MDEiLCJ1c2VyTmFtZSI6Iu2Zjeq4uOyyoCIsInJvbGUiOiJDVVNUT01FUiIsImVtYWlsIjoiY3VzdDAxQGV4YW1wbGUuY29tIiwidG9rZW4iOm51bGwsInJvb21JZCI6bnVsbCwiY29tcGFueUlkIjoiYXB0MDAxIn0=&
roomId=room-885162ab

5. [고객] /api/customer/chatbot
   - 고객의 챗봇 상담방을 생성하고 세션/목록을 갱신
   ㅁ curl 명령(Bash 기준)
      curl -v 'http://localhost:28070/api/customer/chatbot' \
        -X 'POST' \
        -H 'Authorization: Bearer eyJ1c2VySWQiOiJjdXN0MDEiLCJ1c2VyTmFtZSI6Iu2Zjeq4uOyyoCIsInJvbGUiOiJDVVNUT01FUiIsImVtYWlsIjoiY3VzdDAxQGV4YW1wbGUuY29tIiwidG9rZW4iOm51bGwsInJvb21JZCI6bnVsbCwiY29tcGFueUlkIjoiYXB0MDAxIn0='
   ㅁ 비정상결과 > STATUS : 401
   ㅁ 정상결과 > STATUS:200
      {
          "roomId": "room-ea233eac",
          "roomName": "cust01",
          "members": [
          ],
          "status": "BOT",
          "assignedAgent": null,
          "createdAt": 1769405639509,
          "lastActivityAt": 1769405639509,
          "custId": null
      }


4. [상담사] Authorization 헤더의 토큰을 검증해 현재 상담원 정보 반환
   ㅁ curl 명령(Bash 기준)
      curl -v -X GET 'http://localhost:28070/api/agent/me' \
      -H 'Authorization: Bearer eyJ1c2VySWQiOiJhZ2VudDAxIiwidXNlck5hbWUiOiLsg4Hri7Tsm5AtMDEiLCJyb2xlIjoiQUdFTlQiLCJlbWFpbCI6ImFnZW50MDFAYWljYy5jb20iLCJ0b2tlbiI6bnVsbCwicm9vbUlkIjpudWxsLCJjb21wYW55SWQiOiJhcHQwMDEifQ=='

   ㅁ 처리 로직 > 세션에 저장되어 있는 AGENT_TOKEN 헤더의 토큰을 검증해 현재 상담원 정보 반환
   ㅁ 비정상결과 > STATUS : 401
   ㅁ 정상결과 > STATUS : 200
      {
          "userId": "agent01",
          "userName": "상담원-01",
          "role": "AGENT",
          "email": "agent01@aicc.com",
          "token": "eyJ1c2Vy . .  ==",
          "roomId": null,
          "companyId": "apt001"
      }


3. [상담사] 로그인 요청을 인증 서비스로 전달하고 토큰/프로필 반환
   ㅁ curl 명령(Bash 기준)
      curl -v -X POST 'http://localhost:28070/api/agent/login?id=agent01&password=1234'
   ㅁ 처리 로직 > ID와 PASSWORD로 로그인 처리 후 토큰 등 응답
   ㅁ 비정상결과 > STATUS : 401
   ㅁ 정상결과 > STATUS : 200
      {
          "userId": "agent01",
          "userName": "상담원-01",
          "role": "AGENT",
          "email": "agent01@aicc.com",
          "token": "eyJ1c2VySW . . hcHQwMDEifQ==",
          "roomId": null,
          "companyId": "apt001"
      }

2. [고객] 회사별 고객 로그인 처리
   ㅁ curl 명령(Bash 기준)
      curl -v -X POST 'http://localhost:28070/api/customer/apt001/login?id=cust01&password=1234'
   ㅁ 처리 로직 > ID와 PASSWORD로 로그인 처리 후 토큰 등 응답
   ㅁ 비정상(STATUS:401)
   ㅁ 정상(STATUS:200) 결과
      {
          "userId": "cust01",
          "userName": "홍길철",
          "role": "CUSTOMER",
          "email": "cust01@example.com",
          "token": "eyJ1c2VyS . . . In0=",
          "roomId": null,
          "companyId": "apt001"
      }

1. [고객] 헤더의 토큰을 검증해 현재 [사용자] 정보 반환
   ㅁ 처리 로직 > 세션스토리지의 AUTH_TOKEN 값을 읽어 headers: { 'Authorization': 'Bearer ' + token }에 실어 전달후 응답 처리
   ㅁ curl 명령(Bash 기준)
      curl -v -X 'GET' 'http://localhost:28070/api/me' \
      -H 'Authorization: Bearer eyJ1c2VySWQiOiJjdXN0MDEiLCJ1c2VyTmFtZSI6Iu2Zjeq4uOyyoCIsInJvbGUiOiJDVVNUT01FUiIsImVtYWlsIjoiY3VzdDAxQGV4YW1wbGUuY29tIiwidG9rZW4iOm51bGwsInJvb21JZCI6bnVsbCwiY29tcGFueUlkIjoiYXB0MDAxIn0='

   ㅁ 정상결과 > status : 200
      {  "userId":"cust01",
         "userName":"홍길철",
         "role":"CUSTOMER",
         "email":"cust01@example.com",
         "token":"eyJ1c . . xIn0=",
         "roomId":null,
         "companyId":"apt001"
      }
   ㅁ 비정상 결과 > 401

0. StompJS와 WebSocket 통신 및 지원 확인 라이브러리 API
    curl 'http://localhost:28070/ws-chat/info?token=eyJ1c2VySWQiOiJhZ2VudDAxIiwidXNlck5hbWUiOiLsg4Hri7Tsm5AtMDEiLCJyb2xlIjoiQUdFTlQiLCJlbWFpbCI6ImFnZW50MDFAYWljYy5jb20iLCJ0b2tlbiI6bnVsbCwicm9vbUlkIjpudWxsLCJjb21wYW55SWQiOiJhcHQwMDEifQ==&t=1769403915004'

#### ■ /api/agent/rooms (전체채팅방목록 반환)
현재 고객이 상담시작한 채팅방의 목록을 조회한다.
* 요청

```
curl -X GET 'http://localhost:28070/api/agent/rooms'
```

* 응답

```
[ {
  "creatorId" : "cust01",
  "roomId" : "room-591a63d1",
  "roomName" : "배송문의",
  "members" : [ "cust01" ],
  "status" : "BOT",
  "assignedAgent" : null,
  "createdAt" : 1770178696289,
  "lastActivityAt" : 1770178696289,
  "custId" : "cust01"
}, {
  "creatorId" : "cust02",
  "roomId" : "room-ba4e0191",
  "roomName" : "배송문의",
  "members" : [ "cust02" ],
  "status" : "BOT",
  "assignedAgent" : null,
  "createdAt" : 1770178697651,
  "lastActivityAt" : 1770178697651,
  "custId" : "cust02"
} ]
```


#### ■ /api/agent/rooms (전체채팅방목록 반환)
현재 고객이 상담시작한 채팅방의 목록을 조회한다.
* 요청

```
curl -X POST 'http://localhost:28070/api/agent/rooms'
```

* 응답

```
[ {
  "creatorId" : "cust01",
  "roomId" : "room-591a63d1",
  "roomName" : "배송문의",
  "members" : [ "cust01" ],
  "status" : "BOT",
  "assignedAgent" : null,
  "createdAt" : 1770178696289,
  "lastActivityAt" : 1770178696289,
  "custId" : "cust01"
}, {
  "creatorId" : "cust02",
  "roomId" : "room-ba4e0191",
  "roomName" : "배송문의",
  "members" : [ "cust02" ],
  "status" : "BOT",
  "assignedAgent" : null,
  "createdAt" : 1770178697651,
  "lastActivityAt" : 1770178697651,
  "custId" : "cust02"
} ]
```

#### ■ /api/customer/logout (고객의 채팅방로그아웃???)
현재 고객이 채팅방을 나간다.
* 요청

```
curl -X POST 'http://localhost:28070/api/customer'
```

* 응답

```
[ {
  "creatorId" : "cust01",
  "roomId" : "room-591a63d1",
  "roomName" : "배송문의",
  "members" : [ "cust01" ],
  "status" : "BOT",
  "assignedAgent" : null,
  "createdAt" : 1770178696289,
  "lastActivityAt" : 1770178696289,
  "custId" : "cust01"
}, {
  "creatorId" : "cust02",
  "roomId" : "room-ba4e0191",
  "roomName" : "배송문의",
  "members" : [ "cust02" ],
  "status" : "BOT",
  "assignedAgent" : null,
  "createdAt" : 1770178697651,
  "lastActivityAt" : 1770178697651,
  "custId" : "cust02"
} ]
```
<a id="api-customer-chat-end"></a>
### api/customer/chat-end 고객 상담 종료
고객이 채팅방을 나간다.
* 요청
```
ew0KICAidXNlcklkIiA6ICJjdXN0MDEiLA0KICAidXNlck5hbWUiIDogIu2Zjeq4uOyyoCIsDQogICJyb2xlIiA6ICJDVVNUT01FUiIsDQogICJlbWFpbCIgOiAiY3VzdDAxQGV4YW1wbGUuY29tIiwNCiAgInRva2VuIiA6IG51bGwsDQogICJyb29tSWQiIDogInJvb20tYjQzZGY3YzEiLA0KICAiY29tcGFueUlkIiA6ICJhcHQwMDEiLA0KICAic3RhdHVzIiA6IDANCn0
```
```bash
curl 'http://localhost:28070/api/customer/chat-end' \
  -X 'POST' \
  -H 'Authorization: Bearer ew0KICAidXNlcklkIiA6ICJjdXN0MDEiLA0KICAidXNlck5hbWUiIDogIu2Zjeq4uOyyoCIsDQogICJyb2xlIiA6ICJDVVNUT01FUiIsDQogICJlbWFpbCIgOiAiY3VzdDAxQGV4YW1wbGUuY29tIiwNCiAgInRva2VuIiA6IG51bGwsDQogICJyb29tSWQiIDogInJvb20tYjQzZGY3YzEiLA0KICAiY29tcGFueUlkIiA6ICJhcHQwMDEiLA0KICAic3RhdHVzIiA6IDANCn0=' \
  -H 'Content-Type: application/json'
```

**Request Body**
```json
{
  "roomId": "roomId"
}
```

### 내 소개
여기에 자기소개 내용을 작성합니다.
<!-- ■ -->
<a id="api_agent_status"></a>
### □ api/agent/status 상담사 상태 조회


<a id="api_agent_status_status"></a>
### ■ api/agent/status/{status} 상담사 상태 변경

* Parameters
 
| Type   | 이름           | 값                                     | 설명                       |
|--------|---------------|-----------------------------------------|--------------------------|
| Header | Authorization |                                         | 로그인 시 수신한 token |
| Path   | status        | NONE:초기상태<br>WATING: 대기<br>WORKING:대기종료 | WATING이면 상담원 연결 요청시자동 연결분 |


**Request Body**
```json
{

}
```

<a id="api_agent_availability"></a>
### ■ api/agent/availability 상담사 가용성 조회
* Method : GET
* Parameters : NONE
* Request
```
curl 'http://localhost:28070/api/agent/availability' 
```

* Response
```json
{
  "available" : true,
  "agentCount" : 2,
  "waitingAgentCount" : 1
}
```