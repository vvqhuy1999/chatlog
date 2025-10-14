package com.example.chatlog.controller;

import com.example.chatlog.entity.ChatSessions;
import com.example.chatlog.service.ChatSessionsService;
import com.example.chatlog.repository.ChatMessagesRepository;
import com.example.chatlog.entity.ChatMessages;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat-sessions")
@CrossOrigin(origins = "*")
public class ChatSessionsController {

    @Autowired
    private ChatSessionsService chatSessionsService;
    @Autowired
    private ChatMessagesRepository chatMessagesRepository;


    // Lấy tất cả sessions
    @GetMapping
    public ResponseEntity<List<ChatSessionSummaryDto>> getAllChatSessions() {
        try {
            List<ChatSessions> chatSessions = chatSessionsService.findAll();
            List<ChatSessionSummaryDto> result = chatSessions.stream()
                .map(ChatSessionSummaryDto::fromEntity)
                .collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Lấy messages theo sessionId
    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<List<ChatMessages>> getMessagesBySession(@PathVariable Long sessionId) {
        try {
            List<ChatMessages> messages = chatMessagesRepository.findByChatSessionsSessionIdOrderByTimestampAsc(sessionId);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Xóa session
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteChatSession(@PathVariable Long sessionId) {
        try {
            ChatSessions existingSession = chatSessionsService.findById(sessionId);
            if (existingSession == null) {
                return ResponseEntity.notFound().build();
            }
            chatSessionsService.deleteById(sessionId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

@AllArgsConstructor
class ChatSessionSummaryDto {
    public Long sessionId;
    public String title;
    public java.time.LocalDateTime createdAt;
    public java.time.LocalDateTime lastActiveAt;

    static ChatSessionSummaryDto fromEntity(ChatSessions s) {
        return new ChatSessionSummaryDto(s.getSessionId(), s.getTitle(), s.getCreatedAt(), s.getLastActiveAt());
    }
}

