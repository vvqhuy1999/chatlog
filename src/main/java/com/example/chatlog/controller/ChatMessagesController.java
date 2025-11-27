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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            @SuppressWarnings("unchecked")
            Map<String, Object> elasticsearchComparison = (Map<String, Object>) comparisonResult.get("elasticsearch_comparison");
            
            if (responseGenerationComparison != null) {
                // Lưu response OpenAI (KHÔNG gọi AI lại)
                @SuppressWarnings("unchecked")
                Map<String, Object> openaiResponseData = (Map<String, Object>) responseGenerationComparison.get("openai");
                @SuppressWarnings("unchecked")
                Map<String, Object> openaiEsData = elasticsearchComparison != null 
                    ? (Map<String, Object>) elasticsearchComparison.get("openai") 
                    : null;
                    
                if (openaiResponseData != null) {
                    ChatMessages openaiMessage = new ChatMessages();
                    String openaiBody = (String) openaiResponseData.get("response");
                    
                    // Lấy actualQuery từ elasticsearch result (query thực sự được thực thi, đã sửa nếu có retry)
                    if (openaiEsData != null) {
                        String actualQuery = (String) openaiEsData.get("query");
                        openaiBody = replaceQueryInResponse(openaiBody, actualQuery);
                    }
                    
                    openaiMessage.setContent("🔵 **OpenAI Response:**\n\n" + openaiBody);
                    openaiMessage.setSender(ChatMessages.SenderType.AI);
                    ChatMessages savedOpenaiMessage = chatMessagesService.saveWithoutAiResponse(sessionId, openaiMessage);
                    System.out.println("[ChatMessagesController] Đã lưu phản hồi OpenAI với ID: " + savedOpenaiMessage.getMessageId());
                    
                    // Thêm thông tin về message đã lưu vào response
                    comparisonResult.put("saved_openai_message_id", savedOpenaiMessage.getMessageId());
                }

                // Lưu response OpenRouter (KHÔNG gọi AI lại)
                @SuppressWarnings("unchecked")
                Map<String, Object> openrouterResponseData = (Map<String, Object>) responseGenerationComparison.get("openrouter");
                @SuppressWarnings("unchecked")
                Map<String, Object> openrouterEsData = elasticsearchComparison != null 
                    ? (Map<String, Object>) elasticsearchComparison.get("openrouter") 
                    : null;
                    
                if (openrouterResponseData != null) {
                    ChatMessages openrouterMessage = new ChatMessages();
                    String openrouterBody = (String) openrouterResponseData.get("response");
                    
                    // Lấy actualQuery từ elasticsearch result (query thực sự được thực thi, đã sửa nếu có retry)
                    if (openrouterEsData != null) {
                        String actualQuery = (String) openrouterEsData.get("query");
                        openrouterBody = replaceQueryInResponse(openrouterBody, actualQuery);
                    }
                    
                    openrouterMessage.setContent("🟠 **OpenRouter Response:**\n\n" + openrouterBody);
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
    
    /**
     * Thay thế phần "Query đã sử dụng" trong AI response bằng actualQuery thực sự được thực thi
     * Điều này đảm bảo người dùng luôn thấy query chính xác (kể cả sau khi retry/auto-fix)
     */
    private String replaceQueryInResponse(String responseBody, String actualQuery) {
        if (responseBody == null) {
            return "";
        }
        if (actualQuery == null || actualQuery.isBlank() || "N/A".equalsIgnoreCase(actualQuery.trim())) {
            return responseBody;
        }
        
        // Nếu query đang là 1 dòng dài (không có newline) thì format lại cho dễ đọc
        String displayQuery = formatJsonPretty(actualQuery);
        
        // Pattern để tìm phần "Query đã sử dụng:" với code block
        // Hỗ trợ cả **Query đã sử dụng:** và Query đã sử dụng:
        Pattern pattern = Pattern.compile(
            "(\\*\\*Query đã sử dụng:\\*\\*|Query đã sử dụng:)\\s*```json\\s*[\\s\\S]*?```",
            Pattern.MULTILINE
        );
        
        String replacement = "**Query đã sử dụng:**\n```json\n" + displayQuery + "\n```";
        Matcher matcher = pattern.matcher(responseBody);
        
        if (matcher.find()) {
            // Thay thế query cũ bằng actualQuery
            return matcher.replaceFirst(Matcher.quoteReplacement(replacement));
        }
        
        // Nếu không tìm thấy pattern, không thêm gì cả (để nguyên response)
        return responseBody;
    }
    
    /**
     * Format JSON string thành dạng đẹp với indentation.
     * Nếu JSON đã có xuống dòng sẵn thì giữ nguyên định dạng của nó.
     */
    private String formatJsonPretty(String jsonString) {
        // Nếu đã có newline (định dạng sẵn) thì giữ nguyên
        if (jsonString.contains("\n") || jsonString.contains("\r")) {
            return jsonString;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            // Sử dụng custom pretty printer với 2-space indent, gọn gàng hơn
            com.fasterxml.jackson.core.util.DefaultIndenter indenter = 
                new com.fasterxml.jackson.core.util.DefaultIndenter("  ", "\n");
            com.fasterxml.jackson.core.util.DefaultPrettyPrinter printer = 
                new com.fasterxml.jackson.core.util.DefaultPrettyPrinter();
            printer.indentArraysWith(indenter);
            printer.indentObjectsWith(indenter);
            
            Object json = mapper.readValue(jsonString, Object.class);
            return mapper.writer(printer).writeValueAsString(json);
        } catch (Exception e) {
            // Nếu không parse được JSON, trả về nguyên bản
            return jsonString;
        }
    }
}

