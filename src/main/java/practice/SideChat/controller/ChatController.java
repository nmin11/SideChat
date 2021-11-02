package practice.SideChat.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import practice.SideChat.dto.ChatMessage;
import practice.SideChat.repository.ChatRoomRepository;
import practice.SideChat.service.ChatService;
import practice.SideChat.service.JwtTokenProvider;

@RequiredArgsConstructor
@Controller
public class ChatController {

    private final JwtTokenProvider jwtTokenProvider;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatService chatService;

    @MessageMapping("/chat/message")
    public void message(ChatMessage message, @Header("token") String token) {
        String nickname = jwtTokenProvider.getUserNameFromJwt(token);

        //로그인한 회원 정보로 대화명 설정
        message.setSender(nickname);

        //채팅방 인원 수 체킹
        message.setUserCount(chatRoomRepository.getUserCount(message.getRoomId()));

        //WebSocket 에 발행된 메시지를 redis 로 발행 (publish)
        chatService.sendChatMessage(message);
    }

}