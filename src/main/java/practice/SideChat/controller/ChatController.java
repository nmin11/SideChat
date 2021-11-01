package practice.SideChat.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import practice.SideChat.dto.ChatMessage;
import practice.SideChat.repository.ChatRoomRepository;
import practice.SideChat.service.RedisPublisher;

@RequiredArgsConstructor
@Controller
public class ChatController {

    private final RedisPublisher redisPublisher;
    private final ChatRoomRepository chatRoomRepository;

    @MessageMapping("/chat/message")
    public void message(ChatMessage message) {
        if (ChatMessage.MessageType.ENTER.equals(message.getType())) {
            chatRoomRepository.enterChatRoom(message.getRoomId());
            message.setMessage(message.getSender() + " 님 입장! 🤩");
        }
        //WebSocket 에 발행된 메시지를 Redis 로 발행 (publish)
        redisPublisher.publish(chatRoomRepository.getTopic(message.getRoomId()), message);
    }

}