package com.example.chatlog.controller;

import com.example.chatlog.dto.ChatRequest;
import com.example.chatlog.entity.ChatMessages;
import com.example.chatlog.entity.ChatSessions;
import com.example.chatlog.service.ChatMessagesService;
import com.example.chatlog.service.ChatSessionsService;
import com.example.chatlog.service.impl.AiServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat-messages")
@CrossOrigin(origins = "*")
public class ChatMessagesController {

    @Autowired
    private ChatMessagesService chatMessagesService;

    @Autowired
    private ChatSessionsService chatSessionsService;

    @Autowired
    private AiServiceImpl aiServiceImpl;



    // Lấy tất cả messages
    @GetMapping
    public ResponseEntity<List<ChatMessages>> getAllChatMessagesBySession(@PathVariable Long sessionId) {
        try {
            List<ChatMessages> chatMessages = chatMessagesService.findAllBySessionId(sessionId);

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

    /**
     * API để gửi tin nhắn thường (single AI mode) - chỉ OpenAI xử lý
     * @param sessionId Session ID
     * @param chatRequest Tin nhắn từ user
     * @return Phản hồi từ OpenAI
     */
    @PostMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> sendMessage(
        @PathVariable Long sessionId,
        @RequestBody ChatRequest chatRequest) {
        
        try {
            System.out.println("[ChatMessagesController] Bắt đầu chế độ single AI cho phiên: " + sessionId);
            System.out.println("[ChatMessagesController] Tin nhắn người dùng: " + chatRequest.message());

            // Bước 1: Lưu tin nhắn người dùng và nhận phản hồi AI (normal flow)
            ChatMessages userMessage = new ChatMessages();
            userMessage.setContent(chatRequest.message());
            userMessage.setSender(ChatMessages.SenderType.USER);
            
            // Sử dụng save() thông thường - sẽ tự động gọi AI và lưu cả user + AI message
            ChatMessages aiResponse = chatMessagesService.save(sessionId, userMessage);
            
            System.out.println("[ChatMessagesController] Đã lưu phản hồi AI với ID: " + aiResponse.getMessageId());

            // Tạo response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("mode", "single_ai");
            response.put("ai_response", aiResponse.getContent());
            response.put("saved_ai_message_id", aiResponse.getMessageId());
            response.put("session_id", sessionId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("[ChatMessagesController] Single AI mode failed: " + e.getMessage());
            e.printStackTrace();

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Single AI mode failed: " + e.getMessage());
            errorResponse.put("session_id", sessionId);

            return ResponseEntity.status(500).body(errorResponse);
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

    /**
     * API để tạo session mới và gửi tin nhắn đầu tiên với comparison mode
     * @param chatRequest Tin nhắn đầu tiên từ user
     * @return Kết quả tạo session và so sánh từ cả 2 model
     */
    @PostMapping("/start-comparison")
    public ResponseEntity<Map<String, Object>> startSessionWithComparison(
        @RequestBody ChatRequest chatRequest) {
        
        try {
            System.out.println("[ChatMessagesController] Tạo session mới với comparison mode");
            System.out.println("[ChatMessagesController] Tin nhắn đầu tiên: " + chatRequest.message());

            // Bước 1: Tạo session mới
            ChatSessions newSession = new ChatSessions();
            newSession.setTitle(chatRequest.message().length() > 50 ? 
                chatRequest.message().substring(0, 50) + "..." : 
                chatRequest.message());
            ChatSessions savedSession = chatSessionsService.save(newSession);
            
            System.out.println("[ChatMessagesController] Đã tạo session mới với ID: " + savedSession.getSessionId());

            // Bước 2: Gọi comparison mode với session mới
            return sendMessageWithComparison(savedSession.getSessionId(), chatRequest);

        } catch (Exception e) {
            System.out.println("[ChatMessagesController] Tạo session với comparison mode thất bại: " + e.getMessage());
            e.printStackTrace();

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to create session with comparison: " + e.getMessage());

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * API để tạo session mới và gửi tin nhắn đầu tiên với single AI mode
     * @param chatRequest Tin nhắn đầu tiên từ user
     * @return Kết quả tạo session và phản hồi từ AI
     */
    @PostMapping("/start-single")
    public ResponseEntity<Map<String, Object>> startSessionWithSingleAI(
        @RequestBody ChatRequest chatRequest) {
        
        try {
            System.out.println("[ChatMessagesController] Tạo session mới với single AI mode");
            System.out.println("[ChatMessagesController] Tin nhắn đầu tiên: " + chatRequest.message());

            // Bước 1: Tạo session mới
            ChatSessions newSession = new ChatSessions();
            newSession.setTitle(chatRequest.message().length() > 50 ? 
                chatRequest.message().substring(0, 50) + "..." : 
                chatRequest.message());
            ChatSessions savedSession = chatSessionsService.save(newSession);
            
            System.out.println("[ChatMessagesController] Đã tạo session mới với ID: " + savedSession.getSessionId());

            // Bước 2: Gọi single AI mode với session mới
            return sendMessage(savedSession.getSessionId(), chatRequest);

        } catch (Exception e) {
            System.out.println("[ChatMessagesController] Tạo session với single AI mode thất bại: " + e.getMessage());
            e.printStackTrace();

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to create session with single AI: " + e.getMessage());

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * API để gửi tin nhắn với comparison mode - cả 2 AI đều xử lý
     * @param sessionId Session ID
     * @param chatRequest Tin nhắn từ user
     * @return Kết quả so sánh từ cả 2 model
     */
    @PostMapping("/compare/{sessionId}")
    public ResponseEntity<Map<String, Object>> sendMessageWithComparison(
        @PathVariable Long sessionId,
        @RequestBody ChatRequest chatRequest) {

        try {
            // Log thông tin bắt đầu comparison mode
            System.out.println("[ChatMessagesController] Bắt đầu chế độ so sánh cho phiên: " + sessionId);
            System.out.println("[ChatMessagesController] Tin nhắn người dùng: " + chatRequest.message());

            // Bước 1: Lưu tin nhắn của người dùng vào database (KHÔNG gọi AI response)
            ChatMessages userMessage = new ChatMessages();
            userMessage.setContent(chatRequest.message());
            userMessage.setSender(ChatMessages.SenderType.USER);
            ChatMessages savedUserMessage = chatMessagesService.saveWithoutAiResponse(sessionId, userMessage);
            System.out.println("[ChatMessagesController] Đã lưu tin nhắn người dùng với ID: " + savedUserMessage.getMessageId());

            // Bước 2: Gọi comparison mode từ AiServiceImpl để so sánh 2 AI models
            // Method này sẽ trả về kết quả từ cả OpenAI và OpenRouter
            Map<String, Object> comparisonResult = aiServiceImpl.handleRequestWithComparison(sessionId, chatRequest);

            // Bước 3: Lưu CẢ HAI responses vào database (KHÔNG gọi AI lại để tránh duplicate)
            @SuppressWarnings("unchecked")
            Map<String, Object> responseGenerationComparison = (Map<String, Object>) comparisonResult.get("response_generation_comparison");
            if (responseGenerationComparison != null) {
                // Lưu response OpenAI (KHÔNG gọi AI lại)
                @SuppressWarnings("unchecked")
                Map<String, Object> openaiResponseData = (Map<String, Object>) responseGenerationComparison.get("openai");
                if (openaiResponseData != null) {
                    ChatMessages openaiMessage = new ChatMessages();
                    openaiMessage.setContent("🔵 **OpenAI Response:**\n\n" + (String) openaiResponseData.get("response"));
                    openaiMessage.setSender(ChatMessages.SenderType.AI);
                    ChatMessages savedOpenaiMessage = chatMessagesService.saveWithoutAiResponse(sessionId, openaiMessage);
                    System.out.println("[ChatMessagesController] Đã lưu phản hồi OpenAI với ID: " + savedOpenaiMessage.getMessageId());
                    
                    // Thêm thông tin về message đã lưu vào response
                    comparisonResult.put("saved_openai_message_id", savedOpenaiMessage.getMessageId());
                }

                // Lưu response OpenRouter (KHÔNG gọi AI lại)
                @SuppressWarnings("unchecked")
                Map<String, Object> openrouterResponseData = (Map<String, Object>) responseGenerationComparison.get("openrouter");
                if (openrouterResponseData != null) {
                    ChatMessages openrouterMessage = new ChatMessages();
                    openrouterMessage.setContent("🟠 **OpenRouter Response:**\n\n" + (String) openrouterResponseData.get("response"));
                    openrouterMessage.setSender(ChatMessages.SenderType.AI);
                    ChatMessages savedOpenrouterMessage = chatMessagesService.saveWithoutAiResponse(sessionId, openrouterMessage);
                    System.out.println("[ChatMessagesController] Đã lưu phản hồi OpenRouter với ID: " + savedOpenrouterMessage.getMessageId());
                    
                    // Thêm thông tin về message đã lưu vào response
                    comparisonResult.put("saved_openrouter_message_id", savedOpenrouterMessage.getMessageId());
                }
            }

            // Bước 4: Thêm flag success vào response để frontend biết request thành công
            // Chỉ thêm những metadata cần thiết cho frontend
            comparisonResult.put("success", true);
            comparisonResult.put("saved_user_message_id", savedUserMessage.getMessageId());
            
            // Trả về kết quả so sánh với HTTP 200 OK
            return ResponseEntity.ok(comparisonResult);

        } catch (Exception e) {
            // Log lỗi khi comparison mode gặp sự cố
            System.out.println("[ChatMessagesController] So sánh thất bại: " + e.getMessage());
            e.printStackTrace();

            // Tạo error response với thông tin lỗi chi tiết
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Comparison failed: " + e.getMessage());
            
            // Trả về lỗi với HTTP 500 Internal Server Error
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}

