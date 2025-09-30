package com.example.chatlog.utils;

import java.util.Map;

/**
 * SystemPromptTemplate - Quản lý system prompt cho Spring AI
 *
 * Lớp này cho phép tạo system prompt có thể tùy chỉnh với các tham số động
 * để định hình vai trò, phong cách, phạm vi và quy tắc ứng xử cho AI model.
 *
 * Tính năng chính:
 * - Tạo system prompt với các placeholder
 * - Thay thế các placeholder bằng giá trị thực tế
 * - Hỗ trợ định dạng message cho Spring AI
 *
 * @author ChatLog System
 * @version 1.0
 */
public class SystemPromptTemplate {

    private final String template;

    /**
     * Khởi tạo template với chuỗi định dạng chứa các placeholder
     * 
     * @param template Chuỗi template với các placeholder dạng {name}
     */
    public SystemPromptTemplate(String template) {
        this.template = template;
    }

    /**
     * Tạo message từ template bằng cách thay thế các placeholder với giá trị thực
     * 
     * @param parameters Map chứa các cặp key-value để thay thế placeholder
     * @return Message đã được tạo với các placeholder đã được thay thế
     */
    public Message createMessage(Map<String, String> parameters) {
        String content = replacePlaceholders(template, parameters);
        return new Message(MessageType.SYSTEM, content);
    }

    /**
     * Thay thế các placeholder trong template với giá trị thực tế
     * 
     * @param template Chuỗi template với các placeholder
     * @param parameters Map chứa các cặp key-value để thay thế placeholder
     * @return Chuỗi đã được thay thế placeholder
     */
    private String replacePlaceholders(String template, Map<String, String> parameters) {
        String result = template;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /**
     * Tạo system prompt cho AI Assistant với các tham số tùy chỉnh
     * 
     * @param name Tên của AI Assistant
     * @param role Vai trò của AI (ví dụ: "chuyên gia", "trợ lý", "giáo viên")
     * @param expertise Lĩnh vực chuyên môn (ví dụ: "công nghệ", "y tế", "giáo dục")
     * @param style Phong cách trả lời (ví dụ: "chính thức", "thân thiện", "súc tích")
     * @param constraints Các ràng buộc hoặc giới hạn (ví dụ: "không đưa ra lời khuyên y tế")
     * @return System prompt đã được tùy chỉnh
     */
    public static String createAssistantPrompt(String name, String role, String expertise, String style, String constraints) {
        return String.format("""
                AI Assistant - Tùy chỉnh System Prompt
                
                VAI TRÒ CƠ BẢN
                Bạn là %s, một trợ lý AI %s trong lĩnh vực %s. Nhiệm vụ của bạn là cung cấp thông tin chính xác và hữu ích.
                
                PHONG CÁCH TRẢ LỜI
                - Trả lời theo phong cách %s
                - Sử dụng ngôn ngữ rõ ràng, dễ hiểu
                - Cung cấp thông tin chính xác và cập nhật
                
                GIỚI HẠN VÀ RÀNG BUỘC
                %s
                
                QUY TẮC TƯƠNG TÁC
                - Trả lời câu hỏi một cách trực tiếp
                - Thừa nhận khi không biết câu trả lời
                - Không đưa ra thông tin sai lệch hoặc gây hiểu nhầm
                - Tôn trọng quyền riêng tư và bảo mật
                
                ĐỊNH DẠNG TRẢ LỜI
                - Câu trả lời ngắn gọn và súc tích khi có thể
                - Sử dụng định dạng có cấu trúc khi cần thiết
                - Sử dụng gạch đầu dòng cho danh sách
                - Sử dụng ví dụ khi thích hợp
                """,
                name, role, expertise, style, constraints);
    }

    /**
     * Tạo system prompt cho Elasticsearch Query Generator tương tự như getSystemPrompt
     * nhưng với cấu trúc đơn giản hơn và ít tham số hơn
     * 
     * @param dateContext Ngữ cảnh thời gian hiện tại
     * @param fieldMappings Ánh xạ các trường dữ liệu
     * @param exampleQueries Các ví dụ về truy vấn
     * @return System prompt cho Elasticsearch Query Generator
     */
    public static String createElasticsearchQueryPrompt(String dateContext, String fieldMappings, String exampleQueries) {
        return String.format("""
                Elasticsearch Query Generator - System Prompt
                
                VAI TRÒ CƠ BẢN
                Bạn là chuyên gia tạo truy vấn Elasticsearch. Nhiệm vụ của bạn là tạo ra MỘT truy vấn JSON hợp lệ phù hợp chính xác với yêu cầu của người dùng.
                
                QUY TẮC ĐẦU RA
                - Chỉ trả về đối tượng truy vấn JSON
                - Không giải thích, không bọc, không nhiều truy vấn
                - Cú pháp JSON hợp lệ là bắt buộc
                
                XỬ LÝ THỜI GIAN (Ưu tiên #1)
                Ngữ cảnh hiện tại: %s
                
                Thời gian tương đối (Ưu tiên):
                - "5 phút qua/trước" → {"gte": "now-5m"}
                - "1 giờ qua/trước" → {"gte": "now-1h"}
                - "24 giờ qua/trước" → {"gte": "now-24h"}
                
                Ngày cụ thể:
                - "hôm nay/today" → {"gte": "now/d"}
                - "hôm qua/yesterday" → {"gte": "now-1d/d"}
                
                ÁNH XẠ TRƯỜNG DỮ LIỆU
                %s
                
                VÍ DỤ TRUY VẤN
                %s
                
                ĐỊNH DẠNG PHẢN HỒI
                Chỉ trả về JSON:
                - Đơn giản: {"query":{...},"size":50}
                - Tổng hợp: {"query":{...},"aggs":{...},"size":0}
                """,
                dateContext, fieldMappings, exampleQueries);
    }
    
    /**
     * Template cho Elasticsearch Query DSL Expert
     * Sử dụng với SystemPromptTemplate.createMessage() và Map parameters
     */
    public static final String ELASTICSEARCH_DSL_TEMPLATE = """
            Elasticsearch Query DSL Expert - {name}
            
            VAI TRÒ CƠ BẢN
            Bạn là {name}, một {role} trong lĩnh vực {expertise}. Chuyên môn của bạn là tạo ra các truy vấn Elasticsearch Query DSL chính xác và hiệu quả.
            
            PHONG CÁCH LÀM VIỆC
            - Phong cách: {style}
            - Luôn tập trung vào tính chính xác và hiệu quả
            - Giải thích rõ ràng khi cần thiết
            
            NGUYÊN TẮC ĐẦU RA
            - CHỈ trả về JSON Query DSL hợp lệ
            - KHÔNG bọc trong markdown code blocks
            - MỘT truy vấn duy nhất cho mỗi yêu cầu
            - Cú pháp JSON chuẩn, không trailing comma
            
            NGỮ CẢNH THỜI GIAN
            Thời gian hiện tại: {currentTime}
            
            XỬ LÝ THỜI GIAN (Ưu tiên cao):
            - "X phút qua" → "gte": "now-Xm"
            - "X giờ qua" → "gte": "now-Xh"  
            - "X ngày qua" → "gte": "now-Xd"
            - "hôm nay" → "gte": "now/d"
            - "hôm qua" → "gte": "now-1d/d", "lt": "now/d"
            
            SCHEMA INDEX
            {indexSchema}
            
            CẤP ĐỘ PHỨC TẠP
            {complexityLevel}
            
            QUY TẮC TỐI ƯU:
            1. Filter context cho exact match (không cần scoring)
            2. Điều kiện loại bỏ nhiều docs nhất đặt đầu bool query
            3. Term queries cho exact match thay vì match
            4. Size hợp lý: mặc định 50, tối đa {maxSize}
            5. _source filtering để giảm băng thông
            
            VÍ DỤ CẤU TRÚC:
            {queryExamples}
            
            GIỚI HẠN VÀ RÀNG BUỘC
            {constraints}
            - Không tạo truy vấn có thể gây quá tải hệ thống
            - Luôn validate cú pháp JSON trước khi trả về
            
            ĐỊNH DẠNG CUỐI CÙNG
            Chỉ trả về JSON object: {"query": {...}, "size": 50}
            """;
    
    /**
     * Template đơn giản cho Elasticsearch Query Generator
     */
    public static final String SIMPLE_ELASTICSEARCH_TEMPLATE = """
            Elasticsearch Query Generator - {name}
            
            NHIỆM VỤ: {role} tạo truy vấn Elasticsearch JSON
            CHUYÊN MÔN: {expertise}
            PHONG CÁCH: {style}
            
            QUY TẮC NGHIÊM NGẶT:
            - Chỉ JSON, không giải thích
            - Một truy vấn duy nhất
            - Cú pháp chuẩn JSON
            
            THỜI GIAN: {currentTime}
            TRƯỜNG DỮ LIỆU: {indexFields}
            
            THỜI GIAN TƯƠNG ĐỐI:
            - "5 phút qua" → {"range":{"@timestamp":{"gte":"now-5m"}}}
            - "1 giờ qua" → {"range":{"@timestamp":{"gte":"now-1h"}}}
            - "hôm nay" → {"range":{"@timestamp":{"gte":"now/d"}}}
            
            RÀNG BUỘC: {constraints}
            
            ĐẦU RA: {"query":{...},"size":50}
            """;
    
    /**
     * Ví dụ sử dụng với Map parameters
     */
    public static void exampleUsage() {
        // Tạo template
        SystemPromptTemplate template = new SystemPromptTemplate(ELASTICSEARCH_DSL_TEMPLATE);
        
        // Tạo parameters map
        Map<String, String> params = Map.of(
            "name", "ElasticBot",
            "role", "chuyên gia",
            "expertise", "Elasticsearch và tìm kiếm dữ liệu",
            "style", "thân thiện và chuyên nghiệp",
            "constraints", "Không tạo truy vấn có thể làm crash hệ thống",
            "currentTime", "2024-12-25 14:30:00",
            "indexSchema", "@timestamp:date, level:keyword, message:text, host:keyword, user_id:long",
            "complexityLevel", "Intermediate - Hỗ trợ aggregation và nested queries",
            "maxSize", "1000",
            "queryExamples", """
                Search: {"query":{"bool":{"must":[{"match":{"message":"error"}}],"filter":[{"range":{"@timestamp":{"gte":"now-1h"}}}]}} ,"size":50}
                
                Aggregation: {"query":{"match_all":{}},"aggs":{"levels":{"terms":{"field":"level.keyword","size":5}}},"size":0}
                """
        );
        
        // Tạo message
        Message message = template.createMessage(params);
        
        System.out.println("Message Type: " + message.getType());
        System.out.println("Content: " + message.getContent());
    }
    
    /**
     * Ví dụ với template đơn giản
     */
    public static void simpleExampleUsage() {
        SystemPromptTemplate simpleTemplate = new SystemPromptTemplate(SIMPLE_ELASTICSEARCH_TEMPLATE);
        
        Map<String, String> simpleParams = Map.of(
            "name", "QueryBot",
            "role", "chuyên gia",
            "expertise", "Elasticsearch Query DSL",
            "style", "súc tích và chính xác",
            "constraints", "Không đưa ra truy vấn phức tạp quá mức cần thiết",
            "currentTime", "2024-12-25 14:30",
            "indexFields", "@timestamp, level.keyword, message, host.keyword"
        );
        
        Message simpleMessage = simpleTemplate.createMessage(simpleParams);
        
        System.out.println("Simple Template Result:");
        System.out.println(simpleMessage.getContent());
    }
}

/**
 * Enum định nghĩa các loại message trong Spring AI
 * 
 * Lưu ý: Không thể khai báo là public vì phải nằm trong file riêng
 * Thay vào đó, di chuyển AiServiceImpl và SystemPromptTemplate vào cùng package
 */
enum MessageType {
    SYSTEM,
    USER,
    ASSISTANT
}

/**
 * Lớp đại diện cho một message trong Spring AI
 * 
 * Lưu ý: Không thể khai báo là public vì phải nằm trong file riêng
 * Thay vào đó, di chuyển AiServiceImpl và SystemPromptTemplate vào cùng package
 */
class Message {
    private final MessageType type;
    private final String content;

    public Message(MessageType type, String content) {
        this.type = type;
        this.content = content;
    }

    public MessageType getType() {
        return type;
    }

    public String getContent() {
        return content;
    }
}
