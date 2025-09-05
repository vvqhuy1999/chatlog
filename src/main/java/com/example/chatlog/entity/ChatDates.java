package com.example.chatlog.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "chat_dates")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatDates implements Serializable {
    
    @Id
    @Column(name = "chat_date", nullable = false)
    private LocalDate chatDate;

    @Column(name = "is_pinned", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isPinned = false;

    @Column(name = "created_at", updatable = false, insertable = false, 
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    // Quan hệ OneToMany với chat_sessions
    @JsonManagedReference
    @OneToMany(mappedBy = "chatDates", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ChatSessions> chatSessions;
}
