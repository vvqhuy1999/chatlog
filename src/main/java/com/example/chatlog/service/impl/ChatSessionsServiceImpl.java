package com.example.chatlog.service.impl;

import com.example.chatlog.entity.ChatSessions;
import com.example.chatlog.entity.ChatMessages;
import com.example.chatlog.repository.ChatSessionsRepository;
import com.example.chatlog.service.ChatSessionsService;
import com.example.chatlog.service.ChatMessagesService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatSessionsServiceImpl implements ChatSessionsService {

  @Autowired
  private ChatSessionsRepository chatSessionsRepository;

  @Autowired
  private ChatMessagesService chatMessagesService;

  @Override
  public List<ChatSessions> findAll() {
    return chatSessionsRepository.findAll();
  }

  @Override
  public ChatSessions findById(Long id) {
    return chatSessionsRepository.findById(id).orElse(null);
  }

  @Override
  public ChatSessions save(ChatSessions chatSession) {
    return chatSessionsRepository.save(chatSession);
  }

  @Override
  public void deleteById(Long id) {
    chatSessionsRepository.deleteById(id);
  }

  @Override
  @Transactional
  public ChatSessions createWithFirstMessage(String content) {
    ChatSessions newSession = new ChatSessions();
    String derivedTitle = content != null ? content.trim() : null;
    if (derivedTitle != null && !derivedTitle.isEmpty()) {
      if (derivedTitle.length() > 255) {
        derivedTitle = derivedTitle.substring(0, 255);
      }
      newSession.setTitle(derivedTitle);
    }
    ChatSessions savedSession = chatSessionsRepository.save(newSession);

    if (content != null && !content.isBlank()) {
      ChatMessages userMessage = new ChatMessages();
      userMessage.setSender(ChatMessages.SenderType.USER);
      userMessage.setContent(content);
      chatMessagesService.save(savedSession.getSessionId(), userMessage);
    }

    return savedSession;
  }
}
