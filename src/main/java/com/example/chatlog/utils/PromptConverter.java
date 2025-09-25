package com.example.chatlog.utils;

import org.springframework.ai.chat.messages.SystemMessage;
import java.util.Map;

/**
 * Lớp tiện ích để chuyển đổi giữa các loại message khác nhau
 * và tạo system prompt từ template
 */
public class PromptConverter {

    /**
     * Tạo SystemMessage từ template và các tham số
     * 
     * @param template Template chuỗi với các placeholder
     * @param params Map chứa các cặp key-value để thay thế placeholder
     * @return SystemMessage đã được tạo với các placeholder đã được thay thế
     */
    public static SystemMessage createSystemMessage(String template, Map<String, String> params) {
        SystemPromptTemplate promptTemplate = new SystemPromptTemplate(template);
        String content = promptTemplate.createMessage(params).getContent();
        return new SystemMessage(content);
    }
    
    /**
     * Tạo SystemMessage từ template có sẵn và các tham số
     * 
     * @param templateName Tên của template có sẵn (ELASTICSEARCH_DSL_TEMPLATE hoặc SIMPLE_ELASTICSEARCH_TEMPLATE)
     * @param params Map chứa các cặp key-value để thay thế placeholder
     * @return SystemMessage đã được tạo với các placeholder đã được thay thế
     */
    public static SystemMessage createSystemMessageFromTemplate(String templateName, Map<String, String> params) {
        String template;
        
        if ("ELASTICSEARCH_DSL_TEMPLATE".equals(templateName)) {
            template = SystemPromptTemplate.ELASTICSEARCH_DSL_TEMPLATE;
        } else if ("SIMPLE_ELASTICSEARCH_TEMPLATE".equals(templateName)) {
            template = SystemPromptTemplate.SIMPLE_ELASTICSEARCH_TEMPLATE;
        } else {
            throw new IllegalArgumentException("Unknown template name: " + templateName);
        }
        
        return createSystemMessage(template, params);
    }
}
