package com.example.chatlog.repository;

import com.example.chatlog.entity.ChatDates;
import com.example.chatlog.entity.ChatMessages;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessagesRepository extends JpaRepository<ChatMessages, Long> {

}
