package com.example.chatlog.service.impl;

import com.example.chatlog.dto.ChatRequest;
import com.example.chatlog.dto.IntentType;
import com.example.chatlog.dto.RangeDate;
import com.example.chatlog.service.AiService;
import com.example.chatlog.service.LogApiService;
import com.example.chatlog.util.IntentDetector;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AiServiceImpl implements AiService {
    private final JdbcChatMemoryRepository jdbcChatMemoryRepository;
    private final ChatClient chatClient;

    @Autowired
    private LogApiService logApiService;


    public AiServiceImpl(ChatClient.Builder builder,  JdbcChatMemoryRepository jdbcChatMemoryRepository) {
        this.jdbcChatMemoryRepository = jdbcChatMemoryRepository;

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(jdbcChatMemoryRepository)
                .maxMessages(30)
                .build();


        this.chatClient = builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

    }

    @Override
    public String handleRequest(Long sessionId, ChatRequest chatRequest) {
        IntentType intent = IntentDetector.detectIntent(chatRequest.message());
        String content = "";
        switch (intent) {
            case QUERY_STRING:
            {
                break;
            }
            case SEARCH_BY_DATE:
            {
                RangeDate rangeDate = new RangeDate();
                SystemMessage systemMessage = new SystemMessage("""
                Read the message and generate two values : gte, lte as format 2025-09-06T23:59:59+07:00
                """);

                UserMessage userMessage = new UserMessage(chatRequest.message());

                Prompt prompt = new Prompt(systemMessage, userMessage);

                rangeDate =  chatClient
                        .prompt(prompt)
                        .call()
                        .entity(new ParameterizedTypeReference<RangeDate>() {
                        });
                content =  logApiService.searchByDate(".ds-logs-fortinet_fortigate.log-default-2025.09.02-000001",
                        rangeDate.getGte(),
                        rangeDate.getLte());
                break;
            }
            // TODO: viết thêm case cho các intent khác
            default:
                content = "Xin lỗi, tôi chưa hiểu yêu cầu của bạn.";
        }
        return getAiResponse(sessionId,chatRequest,content);
    }

    @Override
    public String getAiResponse(Long sessionId,ChatRequest chatRequest, String content) {
        String conversationId = sessionId.toString();

        SystemMessage systemMessage = new SystemMessage("""
                You are HPT.AI
                You should respond in a formal voice.
                logData : 
                """ + content);

        UserMessage userMessage = new UserMessage(chatRequest.message());

        Prompt prompt = new Prompt(systemMessage, userMessage);

        return chatClient
                .prompt(prompt)
                .advisors(advisorSpec -> advisorSpec.param(
                        ChatMemory.CONVERSATION_ID, conversationId
                ))
                .call()
                .content();
    }

    @Override
    public String getAiResponse(Long sessionId, MultipartFile file, ChatRequest request, String content) {
        String conversationId = sessionId.toString();

        Media media = Media.builder()
                .mimeType(MimeTypeUtils.parseMimeType(file.getContentType()))
                .data(file.getResource())
                .build();

        return chatClient.prompt()
                .system("")
                .user(promptUserSpec ->promptUserSpec.media()
                        .text(request.message()))
                .advisors(advisorSpec -> advisorSpec.param(
                        ChatMemory.CONVERSATION_ID, conversationId
                ))
                .call()
                .content();
    }


}
