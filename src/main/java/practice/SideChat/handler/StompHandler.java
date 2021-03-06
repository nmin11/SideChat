package practice.SideChat.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;
import practice.SideChat.dto.ChatMessage;
import practice.SideChat.repository.ChatRoomRepository;
import practice.SideChat.service.ChatService;
import practice.SideChat.service.JwtTokenProvider;

import java.security.Principal;
import java.util.Optional;

@Log4j2
@RequiredArgsConstructor
@Component
public class StompHandler implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatService chatService;

    //WebSocket 을 통해 들어온 요청이 처리 되기전 실행
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        // WebSocket 연결시 헤더의 jwt token 검증
        if (StompCommand.CONNECT == accessor.getCommand()) {
            String jwtToken = accessor.getFirstNativeHeader("token");
            log.info("CONNECT : {}", jwtToken);
            jwtTokenProvider.validateToken(jwtToken);
        }

        /**
         1. header 정보에서 구독 destination 정보를 얻고 roomId 추출
         2. 채팅방에 들어온 클라이언트 sessionId를 roomId와 매핑 (추후 특정 세션이 어느 방에 들어가 있는지 확인하는 용도)
         3. 채팅방 인원++
         4. 클라이언트 입장 메시지를 채팅방에 발송 (redis publish)
         */
        else if (StompCommand.SUBSCRIBE == accessor.getCommand()) {
            String roomId = chatService.getRoomId(Optional.ofNullable(
                    (String) message.getHeaders().get("simpDestination"))
                    .orElse("Anonymous User"));

            String sessionId = (String) message.getHeaders().get("simpSessionId");
            chatRoomRepository.setUserEnterInfo(sessionId, roomId);

            chatRoomRepository.plusUserCount(roomId);

            String name = Optional.ofNullable((Principal) message.getHeaders().get("simpUser"))
                    .map(Principal::getName).orElse("Anonymous User");
            chatService.sendChatMessage(ChatMessage.builder()
                            .type(ChatMessage.MessageType.ENTER)
                            .roomId(roomId)
                            .sender(name)
                            .build());

            log.info("SUBSCRIBED {}, {}", name, roomId);
        }

        /**
         1. 연결이 종료된 클라이언트 sessionId를 통해서 채팅방 id 획득
         2. 채팅방 인원--
         3. 클라이언트 퇴장 메시지를 채팅방에 발송 (redis publish)
         */
        else if (StompCommand.DISCONNECT == accessor.getCommand()) {
            String sessionId = (String) message.getHeaders().get("simpSessionId");
            String roomId = chatRoomRepository.getUserEnterRoomId(sessionId);

            chatRoomRepository.minusUserCount(roomId);

            String name = Optional.ofNullable((Principal) message.getHeaders().get("simpUser"))
                    .map(Principal::getName).orElse("Anonymous User");
            chatService.sendChatMessage(ChatMessage.builder()
                            .type(ChatMessage.MessageType.QUIT)
                            .roomId(roomId)
                            .sender(name)
                            .build());
            chatRoomRepository.removeUserEnterInfo(sessionId);

            log.info("DISCONNECTED {}, {}", sessionId, roomId);
        }

        return message;
    }
}