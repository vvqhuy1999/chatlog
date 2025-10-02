package com.example.chatlog.service.impl;

import com.example.chatlog.entity.ChatMessages;
import com.example.chatlog.repository.ChatMessagesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.ArrayList;

/**
 * Service helper để lấy chat history cho context building
 */
@Service 
public class ChatHistoryService {
    
    @Autowired
    private ChatMessagesRepository chatMessagesRepository;
    
    /**
     * Lấy chat history của một session (cached và transaction-safe)
     */
    @Cacheable(value = "session_contexts", key = "#sessionId")
    @Transactional(readOnly = true)
    public List<ChatMessages> getChatHistory(Long sessionId) {
        try {
            if (sessionId == null) {
                System.out.println("[ChatHistoryService] Session ID is null, returning empty history");
                return new ArrayList<>();
            }
            
            // Lấy 10 tin nhắn gần nhất của session (đủ để build context)
            List<ChatMessages> messages = null;
            
            // Sử dụng naming convention method (EAGER fetch sẽ handle session loading)
            messages = chatMessagesRepository.findTop10ByChatSessionsSessionIdOrderByTimestampDesc(sessionId);
            System.out.println("[ChatHistoryService] Retrieved " + messages.size() + " messages for session " + sessionId);
            
            // Validate data integrity
            for (ChatMessages message : messages) {
                System.out.println("[ChatHistoryService] ✓ Message " + message.getMessageId() + 
                                 " content: " + message.getContent().substring(0, Math.min(50, message.getContent().length())) + "...");
            }
            
            System.out.println("[ChatHistoryService] Successfully loaded " + messages.size() + 
                             " messages for session " + sessionId);
            return messages;
            
        } catch (Exception e) {
            System.out.println("[ChatHistoryService] Error getting chat history for session " + 
                             sessionId + ": " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}