package com.example.chatlog.service.impl;

import com.example.chatlog.entity.ChatDates;
import com.example.chatlog.repository.ChatDatesRepository;
import com.example.chatlog.service.ChatDatesService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChatDatesServiceImpl implements ChatDatesService {

  @Autowired
  private ChatDatesRepository chatDatesRepository;
  @Override
  public List<ChatDates> findAll() {
    return chatDatesRepository.findAll();
  }

  @Override
  public ChatDates findById(LocalDate id) {
    return chatDatesRepository.findById(id).orElse(null);
  }

  @Override
  public ChatDates save(ChatDates chatDates) {
    return chatDatesRepository.save(chatDates);
  }

  @Override
  public void deleteById(LocalDate id) {
    chatDatesRepository.deleteById(id);
  }

  @Override
  public List<ChatDates> findByIsPinnedTrue() {
    return chatDatesRepository.findByIsPinnedTrue();
  }
}
