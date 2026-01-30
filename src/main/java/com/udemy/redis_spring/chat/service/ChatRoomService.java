package com.udemy.redis_spring.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RListReactive;
import org.redisson.api.RTopicReactive;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * WebSocket 기반 채팅방 서비스
 * Redis Pub/Sub (실시간 메시지 전달) + Redis List (히스토리 저장) 활용
 */
@Service
public class ChatRoomService implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatRoomService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private RedissonReactiveClient client;

    @Value("${server.port:8080}")
    private String serverPort;

    /**
     * WebSocket 연결 처리
     * - Subscribe 로직: 사용자가 보낸 메시지를 처리 (WebSocket → Redis 저장 및 발행)
     * - Publisher 로직: Redis 메시지를 같은 방에 있는 사용자들에게 전달 (Redis → WebSocket)
     */
    @Override
    public Mono<Void> handle(WebSocketSession webSocketSession) {

        String room = getChatRoomName(webSocketSession);
        RTopicReactive topic = this.client.getTopic(room, StringCodec.INSTANCE);

        // Redis List: 채팅 히스토리를 Redis 메모리에 저장 (실행 중에는 유지)
        RListReactive<String> list = this.client.getList("history:" + room, StringCodec.INSTANCE);

        // Subscribe 로직: 클라이언트 메시지 수신 → Redis List 저장 → Redis Topic 발행
        webSocketSession.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(msg -> logMessage(room, msg, "RECEIVED")) // 메시지 수신 로그
//                .flatMap(topic::publish) // history 추가 전 코드
                .flatMap(msg -> list.add(msg).then(topic.publish(msg))) // Redis List 저장 후 Topic 발행
                .doOnError(err -> log.error("❌ Error in subscriber: {}", err.getMessage()))
                .doFinally(s -> log.info("🔌 Subscriber disconnected: {}", s))
                .subscribe(); // WebSocket 메시지 스트림 구독 시작

        // Publisher 로직: Redis Topic 구독 → WebSocket 클라이언트에게 전송
        // - Redis 관점: Subscriber (Topic 구독)
        // - WebSocket 관점: Publisher (클라이언트에게 메시지 발행)
        Flux<WebSocketMessage> flux = topic.getMessages(String.class) // Redis Topic 구독 (실시간 메시지)
                .startWith(list.iterator()) // 히스토리 먼저 전송 (과거 메시지)
                .doOnNext(msg -> logMessage(room, msg, "PUBLISHED")) // 메시지 발행 로그
                .map(webSocketSession::textMessage)
                .doOnError(err -> log.error("❌ Error in publisher: {}", err.getMessage()))
                .doFinally(s -> log.info("🔌 Publisher disconnected: {}", s));

        return webSocketSession.send(flux); // 클라이언트에게 메시지 전송
    }

    /**
     * WebSocket URL의 쿼리 파라미터에서 채팅방 이름 추출
     * 예: ws://localhost:8080/chat?room=game → "game"
     * 파라미터가 없으면 "default" 반환
     */
    private String getChatRoomName(WebSocketSession socketSession){
        URI uri = socketSession.getHandshakeInfo().getUri();
        return UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .toSingleValueMap()
                .getOrDefault("room", "default");
    }

    /**
     * 채팅 메시지 로그 출력
     * JSON 메시지를 파싱하여 누가, 어떤 메시지를 보냈는지 로그로 기록
     */
    private void logMessage(String room, String msg, String action) {
        try {
            JsonNode json = objectMapper.readTree(msg);
            String sender = json.has("sender") ? json.get("sender").asText() : "Unknown";
            String message = json.has("message") ? json.get("message").asText() : msg;

            log.info("📨 [Server:{}] {} | Room: {} | Sender: {} | Message: {}",
                    serverPort, action, room, sender, message);
        } catch (Exception e) {
            // JSON 파싱 실패 시 원본 메시지 로그
            log.info("📨 [Server:{}] {} | Room: {} | Raw: {}",
                    serverPort, action, room, msg);
        }
    }
}