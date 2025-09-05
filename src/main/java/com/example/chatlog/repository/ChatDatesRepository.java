package com.example.chatlog.repository;

import com.example.chatlog.entity.ChatDates;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatDatesRepository extends JpaRepository<ChatDates, LocalDate> {
    
    List<ChatDates> findByIsPinnedTrue();

}
