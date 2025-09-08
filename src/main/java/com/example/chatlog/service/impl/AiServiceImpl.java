package com.example.chatlog.service.impl;

import com.example.chatlog.dto.ChatRequest;
import com.example.chatlog.dto.RequestBody;
import com.example.chatlog.service.AiService;
import com.example.chatlog.service.LogApiService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
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
                .maxMessages(50)
                .build();


        this.chatClient = builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

    }

    @Override
    public String handleRequest(Long sessionId, ChatRequest chatRequest) {

        String fieldLog = logApiService.getFieldLog("logs-fortinet_fortigate.log-default*");


        String content = "";
        RequestBody requestBody = new RequestBody();
        SystemMessage systemMessage = new SystemMessage("""
                Read the message and generate the request body for Elasticsearch.
                If the message contains date values, include gte and lte in the format 2025-09-06T23:59:59+07:00. 
                metadata_field:
                """ + fieldLog);


        ChatOptions chatOptions = ChatOptions.builder()
                .temperature(0D)
                .build();


        UserMessage userMessage = new UserMessage(chatRequest.message());

        Prompt prompt = new Prompt(systemMessage, userMessage);

        requestBody =  chatClient
                .prompt(prompt)
                .options(chatOptions)
                .call()
                .entity(new ParameterizedTypeReference<RequestBody>() {
                });
        content =  logApiService.search("logs-fortinet_fortigate.log-default*",
                requestBody.getBody());
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
                .user(promptUserSpec ->promptUserSpec.media(media)
                        .text(request.message()))
                .advisors(advisorSpec -> advisorSpec.param(
                        ChatMemory.CONVERSATION_ID, conversationId
                ))
                .call()
                .content();
    }
}
