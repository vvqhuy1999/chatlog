package com.example.chatlog.service.impl;

import com.example.chatlog.dto.ChatRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service xử lý tạo phản hồi AI từ dữ liệu Elasticsearch
 * Bao gồm: tạo phản hồi thông thường, so sánh, và xử lý file đính kèm
 */
@Service
public class AiResponseService {
    
    private final ChatClient chatClient;
    
    public AiResponseService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }
    
    /**
     * Tóm tắt và diễn giải dữ liệu log thành ngôn ngữ tự nhiên
     * Sử dụng AI để phân tích kết quả từ Elasticsearch và tạo câu trả lời dễ hiểu
     *
     * @param sessionId ID phiên chat để duy trì ngữ cảnh
     * @param chatRequest Yêu cầu gốc từ người dùng
     * @param content Dữ liệu log từ Elasticsearch hoặc câu trả lời trực tiếp
     * @param query Query đã được sử dụng để tìm kiếm
     * @return Câu trả lời bằng ngôn ngữ tự nhiên
     */
    public String getAiResponse(Long sessionId, ChatRequest chatRequest, String content, String query) {
        String conversationId = sessionId.toString();

        // Lấy thời gian thực của máy
        LocalDateTime currentTime = LocalDateTime.now();
        String currentDate = currentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String currentDateTime = currentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // Định dạng JSON query để hiển thị tốt hơn
        String formattedQuery = query;
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(query);
            formattedQuery = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
        } catch (Exception e) {
            System.out.println("[AiResponseService] Could not format query JSON: " + e.getMessage());
        }

        // Tạo system message hướng dẫn AI cách phản hồi
        SystemMessage systemMessage = new SystemMessage(String.format("""
                You are HPT.AI
                You should respond in a formal voice.
                
                IMPORTANT CONTEXT:
                - Current date: %s
                - Current datetime: %s (Vietnam timezone +07:00)
                - All dates in the query and data are valid and current
                - NEVER mention that dates are "in the future" or incorrect
                - NEVER reference 2023 or any other year as current time
                
                IMPORTANT: Always include the Elasticsearch query used at the end of your response.
                Also include a short justification for key field choices.
                CRITICAL: If the user asks for counts (đếm/số lượng) or totals (tổng), you MUST parse Elasticsearch aggregations and state the numeric answer clearly.
                
                DATA INTERPRETATION RULES:
                - CRITICAL: If hits.total.value = 0 and hits.hits = [], respond with "Không tìm thấy dữ liệu" message. DO NOT generate fake data.
                - If aggregations.total_count.value exists, that is the count of documents.
                - If aggregations.total_bytes.value (or total_packets.value) exists, that is the total metric.
                - If size:0 with only aggregations is returned, base your answer on aggregations instead of hits.
                - If both count and total are present, report both. If only count is present, report count. If no aggregations, use hits.hits length for count (if applicable).
                
              
                
                LOG DATA EXTRACTION RULES:
                For each log entry in hits.hits, extract and display these key fields when available:
                - Người dùng: source.user.name (if available)
                - Địa chỉ nguồn: source.ip 
                - Địa chỉ đích: destination.ip
                - Hành động: fortinet.firewall.action (allow/deny) or event.action
                - Nội dung: event.message or log.message or message
                - Thời gian: @timestamp (format as readable date)
                - Rule: rule.name (if available)
                - Port đích: destination.port (if available)
                - Protocol: network.protocol (if available)
                - Bytes: network.bytes (if available)
                - Quốc gia nguồn: source.geo.country_name (if available)
                - Quốc gia đích: destination.geo.country_name (if available)
                - Mức rủi ro: fortinet.firewall.crlevel (if available)
                - Tấn công: fortinet.firewall.attack (if available)

                logData : %s
                query : %s
                
                Format your response as:
                [Your analysis and summary of the data based on current date %s]
                
                LOG INFORMATION PRESENTATION:
                Present log information in a natural, descriptive format. For each log entry, write a clear description that includes the key details:
                
                Format each log entry as a natural description like:
                "Vào lúc [time], từ địa chỉ [source.ip] đã [action] kết nối đến [destination.ip]:[port] sử dụng giao thức [protocol]. Rule được áp dụng: [rule.name]. Dữ liệu truyền tải: [bytes] bytes."
                
                Include additional details when available:
                - If source.user.name exists: "Người dùng: [source.user.name]"
                - If event.message exists: "Mô tả: [event.message]"
                - If geo information exists: "Từ quốc gia [source.geo.country_name] đến [destination.geo.country_name]"
                - If risk level exists: "Mức rủi ro: [fortinet.firewall.crlevel]"
                - If attack signature exists: "Cảnh báo tấn công: [fortinet.firewall.attack]"
                
                Present multiple entries in a flowing narrative style, grouping similar activities when appropriate.
                
                When the question requests:
                - "đếm số log ..." → Output: "Số log: <number>" (derived from aggregations.total_count.value)
                - "tổng log ..." (tổng số bản ghi) → Output: "Tổng log: <number>" (also aggregations.total_count.value)
                - "tổng bytes/packets ..." → Output: "Tổng bytes/packets: <number>" (from aggregations.total_bytes/total_packets.value)
                
                Field Selection Rationale (concise):
                - Explain why the chosen fields best match the intent, referencing categories when relevant
                - Prefer reasons like: action semantics (fortinet.firewall.action vs event.outcome), traffic volume (network.bytes/packets), direction (network.direction), geo (source/destination.geo.country_name), rule grouping (rule.name vs ruleid), user specificity (source.user.* vs user.*)
                - 3-6 bullets max
                
                **Elasticsearch Query Used:**
                ```json
                %s
                ```
                """
            ,currentDate, currentDateTime, content, query, currentDate, formattedQuery));

        UserMessage userMessage = new UserMessage(chatRequest.message());
        Prompt prompt = new Prompt(systemMessage, userMessage);

        // Gọi AI với ngữ cảnh cuộc trò chuyện để tạo phản hồi
        return chatClient
            .prompt(prompt)
            .options(ChatOptions.builder().temperature(0.0D).build())
            .advisors(advisorSpec -> advisorSpec.param(
                ChatMemory.CONVERSATION_ID, conversationId
            ))
            .call()
            .content();
    }

    /**
     * Phiên bản đặc biệt của getAiResponse dành cho comparison mode
     * Sử dụng conversationId tùy chỉnh để tránh memory contamination giữa các model
     * 
     * @param conversationId Conversation ID tùy chỉnh (ví dụ: "39_openai", "39_openrouter")
     * @param chatRequest Yêu cầu gốc từ user
     * @param content Dữ liệu từ Elasticsearch
     * @param query Query Elasticsearch đã sử dụng
     * @return Phản hồi từ AI
     */
    public String getAiResponseForComparison(String conversationId, ChatRequest chatRequest, String content, String query) {
        // Lấy thời gian thực của máy
        LocalDateTime currentTime = LocalDateTime.now();
        String currentDate = currentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String currentDateTime = currentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // Định dạng JSON query để hiển thị tốt hơn
        String formattedQuery = query;
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(query);
            formattedQuery = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
        } catch (Exception e) {
            System.out.println("[AiResponseService] Could not format query JSON: " + e.getMessage());
        }

        // Tạo system message hướng dẫn AI cách phản hồi
        SystemMessage systemMessage = new SystemMessage(String.format("""
                You are HPT.AI
                You should respond in a formal voice.

                IMPORTANT CONTEXT:
                - Current date: %s
                - Current datetime: %s (Vietnam timezone +07:00)
                - All dates in the query and data are valid and current
                - NEVER mention that dates are "in the future" or incorrect
                - NEVER reference 2023 or any other year as current time

                IMPORTANT: Always include the Elasticsearch query used at the end of your response.
                CRITICAL: You MUST include a section titled exactly: "Lý do chọn các trường" with 3-6 concise bullet points explaining the key field choices.
                CRITICAL: If the user asks for counts (đếm/số lượng) or totals (tổng), you MUST parse Elasticsearch aggregations and state the numeric answer clearly.

                DATA INTERPRETATION RULES:
                - CRITICAL: Nếu có dữ liệu hợp lệ trong hits hoặc aggregations, bạn PHẢI đưa ra kết luận rõ ràng, trực tiếp trả lời đúng ý định của người dùng trước, sau đó cung cấp các chi tiết hỗ trợ (số liệu, người dùng liên quan, mốc thời gian).
                - CRITICAL: If hits.total.value = 0 and hits.hits = [], respond with "Không tìm thấy dữ liệu" message. DO NOT generate fake data.
                - If aggregations.total_count.value exists, that is the count of documents.
                - If aggregations.total_bytes.value (or total_packets.value) exists, that is the total metric.
                - If size:0 with only aggregations is returned, base your answer on aggregations instead of hits.
                - If both count and total are present, report both. If only count is present, report count. If no aggregations, use hits.hits length for count (if applicable).

                NO DATA HANDLING:
                Lưu ý: Chỉ áp dụng khi không có dữ liệu phù hợp.
                When hits.total.value = 0 or aggregations return empty buckets or zero count:
                - Inform the user that no data was found matching their criteria
                - Suggest possible reasons why no data was found
                - Suggest possible modifications to the query
                - DO NOT use a fixed format, respond naturally

                EXAMPLES OF NO DATA SCENARIOS:
                - {"hits":{"total":{"value":0},"hits":[]}} → No matching documents
                - {"aggregations":{"total_count":{"value":0}}} → Count is zero
                - {"aggregations":{"top_users":{"buckets":[]}}} → No aggregation results
                - Empty aggregation buckets = no matching data found

                LOG DATA EXTRACTION RULES:
                For each log entry in hits.hits, extract and display these key fields when available:
                - Người dùng: source.user.name (if available)
                - Địa chỉ nguồn: source.ip 
                - Địa chỉ đích: destination.ip
                - Hành động: fortinet.firewall.action (allow/deny) or event.action
                - Nội dung: event.message or log.message or message
                - Thời gian: @timestamp (format as readable date)
                - Rule: rule.name (if available)
                - Port đích: destination.port (if available)
                - Protocol: network.protocol (if available)
                - Bytes: network.bytes (if available)
                - Quốc gia nguồn: source.geo.country_name (if available)
                - Quốc gia đích: destination.geo.country_name (if available)
                - Mức rủi ro: fortinet.firewall.crlevel (if available)
                - Tấn công: fortinet.firewall.attack (if available)
                - Nếu fortinet.firewall.cfgattr tồn tại hoặc câu hỏi liên quan đến CNHN_ZONE/cfgattr:
                • QUERY PATTERN: {"query":{"bool":{"filter":[{"term":{"source.user.name":"tanln"}},{"match":{"message":"CNHN_ZONE"}}]}},"sort":[{"@timestamp":"asc"}],"size":200}
                • Phân tích chuỗi cfgattr theo quy tắc:
                    1) Tách hai phần trước và sau "->" thành hai danh sách
                    2) Trước khi tách, loại bỏ tiền tố "interface[" (nếu có) và dấu "]" ở cuối (nếu có)
                    3) Mỗi danh sách tách tiếp bằng dấu phẩy hoặc khoảng trắng, chuẩn hóa và loại bỏ khoảng trắng thừa
                    4) "Thêm" = các giá trị có trong danh sách mới nhưng không có trong danh sách cũ
                    5) "Xóa" = các giá trị có trong danh sách cũ nhưng không có trong danh sách mới
                • VÍ DỤ PHÂN TÍCH:
                    Input: "interface[LAB-CNHN MGMT-SW-FW PRINTER-DEVICE SECCAM-CNHN WiFi HPT-GUEST WiFi-HPTVIETNAM WiFi-IoT SERVER_CORE CNHN_Wire_NV CNHN_Wire_Lab->LAB-CNHN MGMT-SW-FW PRINTER-DEVICE SECCAM-CNHN WiFi HPT-GUEST WiFi-HPTVIETNAM WiFi-IoT SERVER_CORE CNHN_Wire_NV]"
                    Bước 1: Tách bằng "->"
                    - Trước: "[LAB-CNHN MGMT-SW-FW PRINTER-DEVICE SECCAM-CNHN WiFi HPT-GUEST WiFi-HPTVIETNAM WiFi-IoT SERVER_CORE CNHN_Wire_NV CNHN_Wire_Lab"
                    - Sau: "LAB-CNHN MGMT-SW-FW PRINTER-DEVICE SECCAM-CNHN WiFi HPT-GUEST WiFi-HPTVIETNAM WiFi-IoT SERVER_CORE CNHN_Wire_NV]"
                    Bước 2: Bỏ tiền tố "interface[" và dấu "]" rồi tách từng danh sách bằng khoảng trắng
                    - Ban đầu: LAB-CNHN, MGMT-SW-FW, PRINTER-DEVICE, SECCAM-CNHN, WiFi, HPT-GUEST, WiFi-HPTVIETNAM, WiFi-IoT, SERVER_CORE, CNHN_Wire_NV, CNHN_Wire_Lab]
                    - Sau: LAB-CNHN, MGMT-SW-FW, PRINTER-DEVICE, SECCAM-CNHN, WiFi, HPT-GUEST, WiFi-HPTVIETNAM, WiFi-IoT, SERVER_CORE, CNHN_Wire_NV
                     Bước 3: So sánh
                    - Thêm: [] (không có)
                    - Xóa: [CNHN_Wire_Lab]
                • Xuất theo timeline (sắp xếp theo @timestamp):
                    - Thời gian: [@timestamp]
                    - Người dùng: [source.user.name]
                    - IP: [source.ip]
                    - Hành động: [message]
                    - Ban đầu: [...]
                    - Sau: [...]
                    - Thêm: [...]
                    - Xóa: [...]
                    Luôn luôn hiển thị cả Ban đầu và Sau, ngay cả khi không có sự thay đổi.
                • Nếu không có "->" trong cfgattr, coi toàn bộ là danh sách hiện tại

                SUMMARIZATION & DEDUPLICATION RULES:
                - Tập trung trả lời trực tiếp câu hỏi của người dùng trước (đúng trọng tâm).
                - Nếu nhiều log giống nhau về các trường chính (ví dụ: source.user.name, source.ip, destination.ip, destination.port, network.protocol, fortinet.firewall.action, rule.name, và nội dung message tương đương), hãy GỘP lại thành MỘT mục mô tả duy nhất và nêu tổng số lần xuất hiện (ví dụ: "xN lần").
                - Chỉ liệt kê chi tiết riêng cho các log có sự khác biệt ý nghĩa (khác người dùng, IP, port, hành động, rule, hoặc thông điệp).
                - Ưu tiên nhóm theo ngữ nghĩa phù hợp với câu hỏi (ví dụ: theo người dùng khi hỏi về hành vi người dùng, theo đích khi hỏi về lưu lượng đến một máy chủ).
                - Giữ văn phong ngắn gọn, tránh lặp lại thông tin không cần thiết.

                logData : %s
                query : %s

                Format your response as:
                [Your analysis and summary of the data based on current date %s]

                Additional guidance:
                - If data exists: Start with a direct, concrete answer to the user’s question (kết luận rõ ràng), then provide brief supporting details and numbers.

                LOG INFORMATION PRESENTATION:
                Present log information in a natural, descriptive format. For each log entry, write a clear description that includes the key details:

                Format each log entry as a natural description like:
                "Vào lúc [time], từ địa chỉ [source.ip] đã [action] kết nối đến [destination.ip]:[port] sử dụng giao thức [protocol]. Rule được áp dụng: [rule.name]. Dữ liệu truyền tải: [bytes] bytes."
                Include additional details when available:
                - If source.user.name exists: "Người dùng: [source.user.name]"
                - If event.message exists: "Mô tả: [event.message]"
                - If geo information exists: "Từ quốc gia [source.geo.country_name] đến [destination.geo.country_name]"
                - If risk level exists: "Mức rủi ro: [fortinet.firewall.crlevel]"
                - If attack signature exists: "Cảnh báo tấn công: [fortinet.firewall.attack]"

                When the question requests:
                - "đếm số log ..." → Output: "Số log: <number>" (derived from aggregations.total_count.value)
                - "tổng log ..." (tổng số bản ghi) → Output: "Tổng log: <number>" (also aggregations.total_count.value)
                - "tổng bytes/packets ..." → Output: "Tổng bytes/packets: <number>" (from aggregations.total_bytes/total_packets.value)

                Lý do chọn các trường:
                - Bạn PHẢI thêm mục này với tiêu đề chính xác: "Lý do chọn các trường".
                - Trình bày 3–6 gạch đầu dòng ngắn gọn, nêu vì sao các trường chính được chọn phù hợp với ý định: hành động (fortinet.firewall.action vs event.action), lưu lượng (network.bytes/packets), hướng (network.direction), địa lý (source/destination.geo.country_name), quy tắc (rule.name vs ruleid), người dùng (source.user.* vs user.*).

                BEFORE SENDING (Self-checklist):
                - The response starts with a direct answer if data exists; otherwise, a natural “Không tìm thấy dữ liệu”.
                - The section "Lý do chọn các trường" exists with 3–6 bullets.
                - The final section includes "**Elasticsearch Query Used:**" followed by the JSON query (pretty-printed if available).
                - Numeric answers for counts/totals are extracted from aggregations when requested.
                - No contradictions with the current date context.
                - Đảm bảo đã gộp các log trùng lặp và nêu tổng số lần xuất hiện.

                **Elasticsearch Query Used:**
                ```json  
                %s  
                ```
                """
            ,currentDate, currentDateTime, content, query, currentDate, formattedQuery));

        UserMessage userMessage = new UserMessage(chatRequest.message());
        Prompt prompt = new Prompt(systemMessage, userMessage);

        // Gọi AI với conversation ID tùy chỉnh để tránh memory contamination
        return chatClient
            .prompt(prompt)
            .options(ChatOptions.builder().temperature(0.0D).build())
            .advisors(advisorSpec -> advisorSpec.param(
                ChatMemory.CONVERSATION_ID, conversationId
            ))
            .call()
            .content();
    }

    /**
     * Xử lý yêu cầu với file đính kèm (hình ảnh, tài liệu, v.v.)
     * Cho phép người dùng gửi file cùng với tin nhắn để AI phân tích
     *
     * @param sessionId ID phiên chat để duy trì ngữ cảnh
     * @param file File được upload bởi người dùng
     * @param request Yêu cầu kèm theo từ người dùng
     * @param content Nội dung bổ sung (nếu có)
     * @return Phản hồi của AI sau khi phân tích file và tin nhắn
     */
    public String getAiResponse(Long sessionId, MultipartFile file, ChatRequest request, String content) {
        String conversationId = sessionId.toString();

        // Tạo đối tượng Media từ file upload
        Media media = Media.builder()
            .mimeType(MimeTypeUtils.parseMimeType(file.getContentType()))
            .data(file.getResource())
            .build();

        // Gọi AI với cả media và text, duy trì ngữ cảnh cuộc trò chuyện
        return chatClient.prompt()
            .system("")
            .user(promptUserSpec ->promptUserSpec.media(media)
                .text(request.message()))
            .advisors(advisorSpec -> advisorSpec.param(
                ChatMemory.CONVERSATION_ID, conversationId
            ))
            .call()
            .content();
    }
}
