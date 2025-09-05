package com.example.chatlog.controller;

import com.example.chatlog.entity.ChatMessages;
import com.example.chatlog.service.ChatMessagesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat-messages")
@CrossOrigin(origins = "*")
public class ChatMessagesController {

    @Autowired
    private ChatMessagesService chatMessagesService;

    // Lấy tất cả messages
    @GetMapping
    public ResponseEntity<List<ChatMessages>> getAllChatMessages() {
        try {
            List<ChatMessages> chatMessages = chatMessagesService.findAll();
            return ResponseEntity.ok(chatMessages);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Lấy message theo ID
    @GetMapping("/{messageId}")
    public ResponseEntity<ChatMessages> getChatMessageById(@PathVariable Long messageId) {
        try {
            ChatMessages chatMessage = chatMessagesService.findById(messageId);
            if (chatMessage != null) {
                return ResponseEntity.ok(chatMessage);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Tạo message mới
    @PostMapping
    public ResponseEntity<ChatMessages> createChatMessage(@RequestBody ChatMessages chatMessage) {
        try {
            ChatMessages savedChatMessage = chatMessagesService.save(chatMessage);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedChatMessage);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // Cập nhật message
    @PutMapping("/{messageId}")
    public ResponseEntity<ChatMessages> updateChatMessage(
            @PathVariable Long messageId,
            @RequestBody ChatMessages chatMessage) {
        try {
            ChatMessages existingMessage = chatMessagesService.findById(messageId);
            if (existingMessage == null) {
                return ResponseEntity.notFound().build();
            }
            chatMessage.setMessageId(messageId);
            ChatMessages updatedChatMessage = chatMessagesService.save(chatMessage);
            return ResponseEntity.ok(updatedChatMessage);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // Xóa message
    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteChatMessage(@PathVariable Long messageId) {
        try {
            ChatMessages existingMessage = chatMessagesService.findById(messageId);
            if (existingMessage == null) {
                return ResponseEntity.notFound().build();
            }
            chatMessagesService.deleteById(messageId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

