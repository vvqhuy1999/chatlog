package com.example.chatlog.repository;

import com.example.chatlog.entity.ChatMessages;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.List;

public interface ChatMessagesRepository extends JpaRepository<ChatMessages, Long> {
    List<ChatMessages> findByChatSessionsSessionIdOrderByTimestampAsc(Long sessionId);
    List<ChatMessages> findByChatSessionsSessionIdOrderByTimestampAsc(Long sessionId);
    List<ChatMessages> findAllByChatSessions_SessionId(Long sessionId);
}
