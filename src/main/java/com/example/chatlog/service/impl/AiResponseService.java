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
import org.springframework.stereotype.Service;

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
     * Phiên bản đặc biệt của getAiResponse dành cho comparison mode
     * Sử dụng conversationId tùy chỉnh để tránh memory contamination giữa các model
     * 
     * @param conversationId Conversation ID tùy chỉnh (ví dụ: "39_openai", "39_openrouter")
     * @param chatRequest Yêu cầu gốc từ user
     * @param content Dữ liệu từ Elasticsearch
     * @param query Query Elasticsearch đã sử dụng
     * @param temperature Temperature cho AI model (0.3 cho OpenAI, 0.7 cho OpenRouter)
     * @return Phản hồi từ AI
     */
    public String getAiResponseForComparison(String conversationId, ChatRequest chatRequest, String content, String query, double temperature) {
        // Lấy thời gian thực của máy
        LocalDateTime currentTime = LocalDateTime.now();
        String currentDate = currentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String currentDateTime = currentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // Định dạng JSON query để hiển thị tốt hơn
        String formattedQuery = query;
        try {
            System.out.println("[AiResponseService] Formatting query: " + query);
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
                                
                ERROR HANDLING RULES:
                - If Elasticsearch returns error (timeout, connection, parsing): Respond with "Đã xảy ra lỗi khi truy vấn dữ liệu: [mô tả lỗi]. Vui lòng thử lại hoặc điều chỉnh câu hỏi."
                - CRITICAL: If hits.total.value >= 10000 (relation: "gte") BUT hits.hits array contains data: You MUST use the data from hits.hits to answer. Add a note like "Tìm thấy hơn 10,000 bản ghi phù hợp, hiển thị [N] bản ghi đầu tiên:" then proceed with the answer using available hits data.
                - CRITICAL: Only respond "không có dữ liệu" if hits.hits is empty [] AND no aggregations exist. If hits.hits has data, you MUST use it even if total.value is large.
                - If required fields are missing in results: Use available fields and note "Một số trường dữ liệu không khả dụng trong kết quả."
                - If index not found: Respond with "Không tìm thấy index dữ liệu. Vui lòng kiểm tra tên index hoặc khoảng thời gian."
                - NEVER generate fake data when errors occur
                                
                TIME RANGE HANDLING:
                - "hôm nay" / "today": {"range": {"@timestamp": {"gte": "now/d", "lte": "now"}}}
                - "hôm qua" / "yesterday": {"range": {"@timestamp": {"gte": "now-1d/d", "lte": "now-1d/d"}}}
                - "tuần này" / "this week": {"range": {"@timestamp": {"gte": "now/w", "lte": "now"}}}
                - "tuần trước" / "last week": {"range": {"@timestamp": {"gte": "now-1w/w", "lte": "now-1w/w"}}}
                - "tháng này" / "this month": {"range": {"@timestamp": {"gte": "now/M", "lte": "now"}}}
                - "tháng trước" / "last month": {"range": {"@timestamp": {"gte": "now-1M/M", "lte": "now-1M/M"}}}
                - "24h qua" / "last 24h": {"range": {"@timestamp": {"gte": "now-24h", "lte": "now"}}}
                - "7 ngày qua" / "last 7 days": {"range": {"@timestamp": {"gte": "now-7d", "lte": "now"}}}
                - "30 ngày qua" / "last 30 days": {"range": {"@timestamp": {"gte": "now-30d", "lte": "now"}}}
                - Always use @timestamp field for time filtering
                - If user specifies exact date/time, convert to ISO8601 format with Vietnam timezone (+07:00)
                                
                QUERY SIZE & PERFORMANCE RULES:
                - Default size: 200 for detailed queries (unless aggregation-only)
                - If aggregation-only query (count, sum, stats): Use size: 0 for better performance
                - If user asks for "tất cả" or "all": Use size: 1000 with note "Hiển thị tối đa 1000 bản ghi đầu tiên"
                - If hits.total.value > size: Note "Tìm thấy [total] bản ghi, hiển thị [size] bản ghi đầu tiên"
                - Maximum size limit: 10000 (Elasticsearch default)
                - Suggest pagination or filtering if results are too large
                                
                FIELD MAPPING PRIORITY (Fallback Chain):
                When a primary field is not available, use the fallback in order:
                - Action: fortinet.firewall.action → event.action → action
                - User: source.user.name → user.name → source.user.id
                - Message: event.message → log.message → message
                - Protocol: network.protocol → network.transport
                - Bytes: network.bytes → (source.bytes + destination.bytes)
                - Source IP: source.ip → client.ip
                - Destination IP: destination.ip → server.ip
                - Port: destination.port → server.port
                - If all fallbacks are missing: Display as "Không rõ" or "N/A"
                                
                NULL/MISSING VALUE HANDLING:
                - If key field (IP, user, action) is null/missing: Display as "Không rõ"
                - If secondary field (geo, risk level) is missing: Omit from description
                - If entire log entry has all key fields missing: Skip entry with note "Bỏ qua [N] bản ghi do thiếu dữ liệu quan trọng"
                - Count null values separately if user explicitly asks for data completeness analysis
                                
                DATA INTERPRETATION RULES:
                - CRITICAL: Nếu có dữ liệu hợp lệ trong hits hoặc aggregations, bạn PHẢI đưa ra kết luận rõ ràng, trực tiếp trả lời đúng ý định của người dùng trước, sau đó cung cấp các chi tiết hỗ trợ (số liệu, người dùng liên quan, mốc thời gian).
                - CRITICAL: If hits.total.value = 0 and hits.hits = [] AND no aggregations: respond with "Không tìm thấy dữ liệu phù hợp với điều kiện tìm kiếm. Vui lòng thử điều chỉnh khoảng thời gian hoặc điều kiện lọc." DO NOT generate fake data.
                - CRITICAL: If hits.total.value >= 10000 (relation: "gte") BUT hits.hits array has items: The hits.hits array contains REAL DATA. You MUST extract and use this data to answer the user's question. Do NOT say "no data" or "too large to display". Show the actual IP addresses, users, or other data from hits.hits.
                - CRITICAL: If hits.hits.length > 0, there IS data available. Use it regardless of total.value size. Only mention "large result set" as an informational note, not as a reason to skip answering.
                - If aggregations.total_count.value exists, that is the count of documents.
                - If aggregations.total_bytes.value (or total_packets.value) exists, that is the total metric.
                - If size:0 with only aggregations is returned, base your answer on aggregations instead of hits.
                - If both count and total are present, report both. If only count is present, report count. If no aggregations, use hits.hits length for count (if applicable).
                                
                ADVANCED AGGREGATION HANDLING:
                - date_histogram: Present as "Phân tích theo thời gian:" with timeline breakdown
                - terms aggregation: Present as "Top [N] [field]:" with ranking and counts
                  - If buckets > 20: Show top 15 and add "... và [N] mục khác"
                - nested aggregations: Parse hierarchy and present as grouped summary with indentation
                - stats/percentiles: Present as "Thống kê:" with min, max, avg, sum
                - cardinality: Present as "Số lượng duy nhất: [value]"
                - If multiple aggregations: Group logically by category
                                
                LOG DATA EXTRACTION RULES:
                For each log entry in hits.hits, extract and display these key fields when available:
                - Người dùng: source.user.name (if available)
                - Địa chỉ nguồn: source.ip\s
                - Địa chỉ đích: destination.ip
                - Hành động: fortinet.firewall.action (allow/deny) or event.action
                - Nội dung: event.message or log.message or message
                - Thời gian: @timestamp (format as readable date DD/MM/YYYY HH:mm:ss)
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
                    - Ban đầu: LAB-CNHN, MGMT-SW-FW, PRINTER-DEVICE, SECCAM-CNHN, WiFi, HPT-GUEST, WiFi-HPTVIETNAM, WiFi-IoT, SERVER_CORE, CNHN_Wire_NV, CNHN_Wire_Lab
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
                - If listing > 30 similar entries: Show top 20 detailed + "... và [N] bản ghi tương tự khác"
                - If listing > 50 entries of any kind: Group by pattern and show "Xuất hiện [N] lần với đặc điểm: [pattern]"
                                
                RESPONSE LENGTH MANAGEMENT:
                - Priority 1: Direct answer to user's question (1-2 sentences)
                - Priority 2: Key statistics/numbers (if applicable)
                - Priority 3: Representative examples (max 20 detailed entries)
                - Priority 4: Summary of remaining data
                - If detailed log listing would exceed 50 entries: Automatically switch to grouped summary format
                - Always provide aggregated insights before raw log details
                                
                Format your response as:
                [Your analysis and summary of the data based on current date %s]
                                
                Additional guidance:
                - If data exists: Start with a direct, concrete answer to the user's question (kết luận rõ ràng), then provide brief supporting details and numbers.
                - CRITICAL: If hits.hits array contains items (even if total.value >= 10000), extract the actual data (IPs, users, timestamps, etc.) from hits.hits and use it to answer. Do NOT skip answering just because total.value is large.
                - Example: If user asks "IP nào truy cập google.com" and hits.hits has 10 items with source.ip, list those 10 IPs even if total.value = 10000. Add note "Tìm thấy hơn 10,000 kết quả, hiển thị [10] IP đầu tiên:" then list them.
                                
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
                                
                BYTE UNIT CONVERSION RULES:
                When displaying network.bytes or any byte values, automatically convert to appropriate units:
                - If bytes >= 1,073,741,824 (1024^3): convert to GB (divide by 1,073,741,824), format as "X.XX GB"
                - If bytes >= 1,048,576 (1024^2): convert to MB (divide by 1,048,576), format as "X.XX MB"
                - If bytes >= 1,024: convert to KB (divide by 1,024), format as "X.XX KB"
                - If bytes < 1,024: keep as bytes, format as "X bytes"
                Always show both the converted unit and the original bytes in parentheses when converting.
                Examples:
                - 152.34 MB (159,744,032 bytes)
                - 2.15 GB (2,308,743,168 bytes)
                - Scientific notation example: 5.4976546E7 -> 52.42 MB (54,976,546 bytes)
                - Scientific notation example (GB): 2.5E9 -> 2.33 GB (2,500,000,000 bytes)
                Note: Scientific notation must be converted to a standard number before applying unit conversion.
                                
                QUERY VALIDATION (Before conceptual execution):
                Self-check these points:
                ✓ Time range is logical (start <= end, not in far future)
                ✓ Field names follow standard ECS or known schema
                ✓ Query size is reasonable (<= 10000)
                ✓ Bool query structure is valid (must/should/filter/must_not)
                ✓ No obvious syntax errors
                ✓ If validation fails: Note the issue in response
                                
                Lý do chọn các trường:
                - Bạn PHẢI thêm mục này với tiêu đề chính xác: "Lý do chọn các trường".
                - Trình bày 3–6 gạch đầu dòng ngắn gọn, nêu vì sao các trường chính được chọn phù hợp với ý định: hành động (fortinet.firewall.action vs event.action), lưu lượng (network.bytes/packets), hướng (network.direction), địa lý (source/destination.geo.country_name), quy tắc (rule.name vs ruleid), người dùng (source.user.* vs user.*).
                - Giải thích việc sử dụng aggregations (nếu có): sum, count, terms, date_histogram, v.v.
                                
                BEFORE SENDING (Self-checklist):
                - CRITICAL CHECK: If hits.hits array has items, you MUST extract and use that data. Do NOT say "no data" or "too large" if hits.hits contains data.
                - The response starts with a direct answer if data exists (from hits.hits or aggregations); otherwise, a natural "Không tìm thấy dữ liệu phù hợp" with suggestion.
                - If error occurred, error message is clear and helpful.
                - The section "Lý do chọn các trường" exists with 3–6 bullets.
                - The final section includes "**Elasticsearch Query Used:**" followed by the JSON query (pretty-printed if available).
                - Numeric answers for counts/totals are extracted from aggregations when requested.
                - No contradictions with the current date context.
                - Đảm bảo đã gộp các log trùng lặp và nêu tổng số lần xuất hiện.
                - If results are large (>30 entries), grouped summary is provided.
                - Time format is DD/MM/YYYY HH:mm:ss for Vietnamese context.

                logData : %s
                
                **Elasticsearch Query Used:**
                ```json  
                %s  
                ```
                """
            ,currentDate, currentDateTime, currentDate, content, formattedQuery));

        UserMessage userMessage = new UserMessage(chatRequest.message());
        // System.out.println("AI trả về systemMessage: " + systemMessage);
        Prompt prompt = new Prompt(systemMessage, userMessage);

        // ✅ Log context được gửi cho AI (để debug)
//        System.out.println("[AiResponseService] 📤 Sending context to AI:");
//        System.out.println("[AiResponseService] 📝 User question: " + chatRequest.message());
//        System.out.println("[AiResponseService] 📊 Content length: " + content.length() + " characters");
//        System.out.println("[AiResponseService] 🔍 Content preview: " + content);
//        System.out.println("[AiResponseService] 🔎 Query: " + query);

        // ✅ Validate inputs before sending to AI
        if (chatRequest == null || chatRequest.message() == null || chatRequest.message().trim().isEmpty()) {
            System.out.println("[AiResponseService] ⚠️ WARNING: chatRequest or message is null/empty");
            return "❌ Error: Invalid request - message is empty";
        }

        // Gọi AI với conversation ID tùy chỉnh để tránh memory contamination
        return chatClient
            .prompt(prompt)
            .options(ChatOptions.builder().temperature(temperature).build())
            .advisors(advisorSpec -> advisorSpec.param(
                ChatMemory.CONVERSATION_ID, conversationId
            ))
            .call()
            .content();
    }


}
