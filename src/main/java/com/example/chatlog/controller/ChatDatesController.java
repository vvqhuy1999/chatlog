package com.example.chatlog.controller;

import com.example.chatlog.entity.ChatDates;
import com.example.chatlog.service.ChatDatesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/chat-dates")
@CrossOrigin(origins = "*")
public class ChatDatesController {

    @Autowired
    private ChatDatesService chatDatesService;

    // Lấy tất cả ngày chat
    @GetMapping
    public ResponseEntity<List<ChatDates>> getAllChatDates() {
        try {
            List<ChatDates> chatDates = chatDatesService.findAll();
            return ResponseEntity.ok(chatDates);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Lấy ngày chat theo ID
    @GetMapping("/{chatDate}")
    public ResponseEntity<ChatDates> getChatDateById(@PathVariable LocalDate chatDate) {
        try {
            ChatDates chatDates = chatDatesService.findById(chatDate);
            if (chatDates != null) {
                return ResponseEntity.ok(chatDates);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Tạo ngày chat mới
    @PostMapping
    public ResponseEntity<ChatDates> createChatDate(@RequestBody ChatDates chatDates) {
        try {
            ChatDates savedChatDate = chatDatesService.save(chatDates);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedChatDate);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // Cập nhật ngày chat
    @PutMapping("/{chatDate}")
    public ResponseEntity<ChatDates> updateChatDate(
            @PathVariable LocalDate chatDate, 
            @RequestBody ChatDates chatDates) {
        try {
            chatDates.setChatDate(chatDate);
            ChatDates updatedChatDate = chatDatesService.save(chatDates);
            return ResponseEntity.ok(updatedChatDate);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // Xóa ngày chat
    @DeleteMapping("/{chatDate}")
    public ResponseEntity<Void> deleteChatDate(@PathVariable LocalDate chatDate) {
        try {
            ChatDates existingChatDate = chatDatesService.findById(chatDate);
            if (existingChatDate == null) {
                return ResponseEntity.notFound().build();
            }
            chatDatesService.deleteById(chatDate);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    
    // Toggle pin status
    @PatchMapping("/{chatDate}/pin")
    public ResponseEntity<ChatDates> togglePin(@PathVariable LocalDate chatDate) {
        try {
            ChatDates chatDates = chatDatesService.findById(chatDate);
            if (chatDates != null) {
                chatDates.setIsPinned(!chatDates.getIsPinned());
                ChatDates updatedChatDate = chatDatesService.save(chatDates);
                return ResponseEntity.ok(updatedChatDate);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Lấy các ngày đã pin
    @GetMapping("/pinned")
    public ResponseEntity<List<ChatDates>> getPinnedChatDates() {
        try {
            List<ChatDates> pinnedDates = chatDatesService.findByIsPinnedTrue();
            return ResponseEntity.ok(pinnedDates);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
