package com.example.chatlog.service.impl;

import com.example.chatlog.dto.ChatRequest;
import com.example.chatlog.entity.ChatMessages;
import com.example.chatlog.entity.ChatSessions;
import com.example.chatlog.repository.ChatMessagesRepository;
import com.example.chatlog.repository.ChatSessionsRepository;
import com.example.chatlog.service.AiService;
import com.example.chatlog.service.ChatMessagesService;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChatMessagesServiceImpl implements ChatMessagesService {

  @Autowired
  private ChatMessagesRepository chatMessagesRepository;
  @Autowired
  private ChatSessionsRepository chatSessionsRepository;

  @Autowired
  private AiService aiService;


  @Override
  public List<ChatMessages> findAllBySessionId(Long sessionId) {

    return chatMessagesRepository.findAllByChatSessions_SessionId(sessionId);
  }

  @Override
  public ChatMessages findById(Long id) {
    return chatMessagesRepository.findById(id).orElse(null);
  }

  @Override
  public ChatMessages save(Long sessionId,ChatMessages chatMessages) {
    ChatSessions chatSessions = chatSessionsRepository.findById(sessionId).orElse(null);
    chatMessages.setChatSessions(chatSessions);
    chatMessagesRepository.save(chatMessages);

    ChatRequest chatRequest = new ChatRequest(chatMessages.getContent());
    ChatMessages aiMessage = new ChatMessages();
    aiMessage.setChatSessions(chatSessions);
    aiMessage.setSender(ChatMessages.SenderType.AI);
    try{
      String response = aiService.handleRequest(sessionId,chatRequest);
      aiMessage.setContent(response);
      return chatMessagesRepository.save(aiMessage);
    }
    catch (Exception e){
      e.printStackTrace();
    }
    aiMessage.setContent("Hệ thống đang gặp sự cố. Mời quay lại sau");
    return aiMessage;
  }

  @Override
  public ChatMessages save(ChatMessages chatMessages) {
    return chatMessagesRepository.save(chatMessages);
  }

  @Override
  public ChatMessages saveWithoutAiResponse(Long sessionId, ChatMessages chatMessages) {
    ChatSessions chatSessions = chatSessionsRepository.findById(sessionId).orElse(null);
    chatMessages.setChatSessions(chatSessions);
    return chatMessagesRepository.save(chatMessages);
  }

  @Override
  public void deleteById(Long id) {
    chatMessagesRepository.deleteById(id);
  }
}
