package com.udemy.redis_spring.chat.service;

import org.redisson.api.RListReactive;
import org.redisson.api.RTopicReactive;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private RedissonReactiveClient client;

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
//                .flatMap(topic::publish) // history 추가 전 코드
                .flatMap(msg -> list.add(msg).then(topic.publish(msg))) // Redis List 저장 후 Topic 발행
                .doOnError(System.out::println)
                .doFinally(s -> System.out.println("Subscriber finally " + s))
                .subscribe(); // WebSocket 메시지 스트림 구독 시작

        // Publisher 로직: Redis Topic 구독 → WebSocket 클라이언트에게 전송
        // - Redis 관점: Subscriber (Topic 구독)
        // - WebSocket 관점: Publisher (클라이언트에게 메시지 발행)
        Flux<WebSocketMessage> flux = topic.getMessages(String.class) // Redis Topic 구독 (실시간 메시지)
                .startWith(list.iterator()) // 히스토리 먼저 전송 (과거 메시지)
                .map(webSocketSession::textMessage)
                .doOnError(System.out::println)
                .doFinally(s -> System.out.println("publisher finally " + s));

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
}