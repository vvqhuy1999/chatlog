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

  @Override
  public List<ChatMessages> findAllBySessionId(Long sessionId) {

    return chatMessagesRepository.findAllByChatSessions_SessionId(sessionId);
  }



  @Override
  public ChatMessages save(Long sessionId,ChatMessages chatMessages) {
    ChatMessages aiMessage = new ChatMessages();
    return aiMessage;
  }



  @Override
  public ChatMessages saveWithoutAiResponse(Long sessionId, ChatMessages chatMessages) {
    ChatSessions chatSessions = chatSessionsRepository.findById(sessionId).orElse(null);
    chatMessages.setChatSessions(chatSessions);
    return chatMessagesRepository.save(chatMessages);
  }


}
