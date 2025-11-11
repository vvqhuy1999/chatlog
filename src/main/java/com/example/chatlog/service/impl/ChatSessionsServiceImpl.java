package com.example.chatlog.service.impl;

import com.example.chatlog.entity.chat.ChatSessions;
import com.example.chatlog.entity.chat.ChatMessages;
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


}
