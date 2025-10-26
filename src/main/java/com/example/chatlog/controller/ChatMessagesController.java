package com.example.chatlog.controller;

import com.example.chatlog.dto.ChatRequest;
import com.example.chatlog.entity.chat.ChatMessages;
import com.example.chatlog.entity.chat.ChatSessions;
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

