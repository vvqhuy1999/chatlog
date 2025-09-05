package com.example.chatlog.service;

import com.example.chatlog.entity.ChatDates;
import java.time.LocalDate;
import java.util.List;


public interface ChatDatesService {

  List<ChatDates> findAll();
  ChatDates findById(LocalDate id);
  ChatDates save(ChatDates chatDates);
  void deleteById(LocalDate id);
  List<ChatDates> findByIsPinnedTrue();

}
