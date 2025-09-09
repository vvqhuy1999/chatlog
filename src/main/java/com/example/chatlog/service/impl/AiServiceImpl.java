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
    private static String fieldLog;

    private final ChatClient chatClient;

    @Autowired
    private LogApiService logApiService;

    public String getFieldLog()
    {
        if (fieldLog == null)
        {
            fieldLog = logApiService.getFieldLog("logs-fortinet_fortigate.log-default*");
        }
        return fieldLog;
    }

    public AiServiceImpl(ChatClient.Builder builder,  JdbcChatMemoryRepository jdbcChatMemoryRepository) {


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

                String content;
        RequestBody requestBody;
        SystemMessage systemMessage = new SystemMessage("""
                Read the message and generate the request body for Elasticsearch.
                If the message contains date values, include gte and lte in the format 2025-09-06T23:59:59+07:00.
                With every request, Only include the fields relevant to the question in the response using _source filtering.
                If the message does not require log data, return query = 0 else 1.
                metadata_field:
                """ + getFieldLog());


        ChatOptions chatOptions = ChatOptions.builder()
                .temperature(0D)
                .build();


        UserMessage userMessage = new UserMessage(chatRequest.message());

        Prompt prompt = new Prompt(systemMessage, userMessage);

        requestBody =  chatClient
                .prompt(prompt)
                .options(chatOptions)
                .call()
                .entity(new ParameterizedTypeReference<>() {
                });

        System.out.println("THong tin quey: "+requestBody.getQuery());

        if (requestBody.getQuery() != 0)
        {
            content =  logApiService.search("logs-fortinet_fortigate.log-default*",
                    requestBody.getBody());
        }

        else
            content = requestBody.getBody();

        return getAiResponse(sessionId,chatRequest,content, requestBody.getBody());
    }


    public String getAiResponse(Long sessionId,ChatRequest chatRequest, String content,String query) {
        String conversationId = sessionId.toString();

        SystemMessage systemMessage = new SystemMessage("""
                You are HPT.AI
                You should respond in a formal voice.
                If the query is executed but no results are found, return the Elasticsearch query body itself, summary of the result query.
                logData :
                """ + content+" query: " + query);

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
