package com.example.chatlog.service.impl;

import com.example.chatlog.dto.ChatRequest;
import com.example.chatlog.dto.RequestBody;
import com.example.chatlog.service.AiService;
import com.example.chatlog.service.LogApiService;
import com.example.chatlog.service.ModelConfigService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class AiServiceImpl implements AiService {
  // Lưu trữ thông tin mapping của Elasticsearch index để tránh gọi lại nhiều lần
  private static String fieldLog;

  // Client để giao tiếp với AI model (Spring AI)
  private final ChatClient chatClient;

  @Autowired
  private LogApiService logApiService;
  
  @Autowired
  private ModelConfigService modelConfigService;
  
  @Autowired
  @Qualifier("openRouterChatClient")
  private RestClient openRouterClient;
  
  // Các service mới được tách ra
  @Autowired
  private AiQueryService aiQueryService;
  
  @Autowired
  private AiComparisonService aiComparisonService;
  
  
  @Autowired
  private AiResponseService aiResponseService;



  /**
   * Constructor khởi tạo AiServiceImpl với ChatClient và memory
   * @param builder ChatClient.Builder để xây dựng client AI
   * @param jdbcChatMemoryRepository Repository lưu trữ lịch sử chat
   */
  public AiServiceImpl(ChatClient.Builder builder,  JdbcChatMemoryRepository jdbcChatMemoryRepository) {

    // Khởi tạo memory để lưu trữ lịch sử chat của người dùng (tối đa 50 tin nhắn)
    ChatMemory chatMemory = MessageWindowChatMemory.builder()
        .chatMemoryRepository(jdbcChatMemoryRepository)
        .maxMessages(50)
        .build();

    // Xây dựng ChatClient với memory advisor để duy trì ngữ cảnh cuộc trò chuyện
    this.chatClient = builder
        .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
        .build();

  }

  /**
   * Hàm chính xử lý yêu cầu của người dùng
   * Quy trình 3 bước:
   * 1. Phân tích câu hỏi và tạo Elasticsearch query (bắt buộc cho tất cả request)
   * 2. Thực hiện tìm kiếm Elasticsearch và lấy dữ liệu log
   * 3. Tóm tắt và trả lời bằng ngôn ngữ tự nhiên
   *
   * @param sessionId ID phiên chat để duy trì ngữ cảnh
   * @param chatRequest Yêu cầu từ người dùng
   * @return Câu trả lời đã được xử lý
   */
  @Override
  public String handleRequest(Long sessionId, ChatRequest chatRequest) {
    try {
      // Bước 1: Tạo Elasticsearch query
      System.out.println("[AiServiceImpl] 🤖 Đang tạo Elasticsearch query...");
      RequestBody requestBody = aiQueryService.generateElasticsearchQuery(sessionId, chatRequest);
      
      // Bước 2: Validation query
      String validationError = aiQueryService.checkBodyFormat(requestBody);
      if (validationError != null) {
        System.out.println("[AiServiceImpl] Query validation failed: " + validationError);
        return validationError;
      }
      
      // Bước 3: Thực hiện tìm kiếm Elasticsearch
      System.out.println("[AiServiceImpl] 🔍 Đang thực hiện tìm kiếm Elasticsearch...");
      String[] result = aiQueryService.getLogData(requestBody, chatRequest);
      String content = result[0];
      String fixedQuery = result[1];
      
      // Kiểm tra lỗi từ Elasticsearch
      if (content != null && content.startsWith("❌")) {
        return content;
      }
      
      // Kiểm tra kết quả rỗng
      if (content != null && aiQueryService.isEmptyElasticsearchResult(content)) {
        System.out.println("[AiServiceImpl] Elasticsearch returned no data, continuing with AI processing");
      }
      
      // Bước 4: Tạo phản hồi AI
      System.out.println("[AiServiceImpl] 💬 Đang tạo phản hồi AI...");
      return aiResponseService.getAiResponse(sessionId, chatRequest, content, fixedQuery);
      
    } catch (Exception e) {
      System.out.println("[AiServiceImpl] ❌ ERROR: " + e.getMessage());
      return "❌ **AI Service Error**\n\n" +
             "Không thể xử lý yêu cầu của bạn.\n\n" +
             "**Chi tiết:** " + e.getMessage() + "\n\n" +
             "💡 **Gợi ý:** Vui lòng thử lại sau hoặc liên hệ admin nếu vấn đề tiếp tục.";
    }
  }


  /**
   * Xử lý yêu cầu của người dùng trong chế độ so sánh, sử dụng cả OpenAI và OpenRouter
   * @param sessionId ID phiên chat để duy trì ngữ cảnh
   * @param chatRequest Yêu cầu từ người dùng
   * @return Kết quả so sánh giữa hai provider
   */
  @Override
  public Map<String, Object> handleRequestWithComparison(Long sessionId, ChatRequest chatRequest) {
    return aiComparisonService.handleRequestWithComparison(sessionId, chatRequest);
  }

}