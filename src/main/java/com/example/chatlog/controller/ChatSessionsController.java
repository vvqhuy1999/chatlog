package com.example.chatlog.controller;

import com.example.chatlog.entity.ChatSessions;
import com.example.chatlog.service.ChatSessionsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/chat-sessions")
@CrossOrigin(origins = "*")
public class ChatSessionsController {

    @Autowired
    private ChatSessionsService chatSessionsService;

    // Lấy tất cả sessions
    @GetMapping
    public ResponseEntity<List<ChatSessions>> getAllChatSessions() {
        try {
            List<ChatSessions> chatSessions = chatSessionsService.findAll();
            return ResponseEntity.ok(chatSessions);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Lấy session theo ID
    @GetMapping("/{sessionId}")
    public ResponseEntity<ChatSessions> getChatSessionById(@PathVariable Long sessionId) {
        try {
            ChatSessions chatSession = chatSessionsService.findById(sessionId);
            if (chatSession != null) {
                return ResponseEntity.ok(chatSession);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Tạo session mới
    @PostMapping
    public ResponseEntity<ChatSessions> createChatSession(@RequestBody ChatSessions chatSession) {
        try {
            ChatSessions savedChatSession = chatSessionsService.save(chatSession);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedChatSession);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // Cập nhật session
    @PutMapping("/{sessionId}")
    public ResponseEntity<ChatSessions> updateChatSession(
            @PathVariable Long sessionId,
            @RequestBody ChatSessions chatSession) {
        try {
            ChatSessions existingSession = chatSessionsService.findById(sessionId);
            if (existingSession == null) {
                return ResponseEntity.notFound().build();
            }
            chatSession.setSessionId(sessionId);
            ChatSessions updatedChatSession = chatSessionsService.save(chatSession);
            return ResponseEntity.ok(updatedChatSession);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
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

