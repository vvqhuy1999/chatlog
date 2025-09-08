package com.example.chatlog.util;

import com.example.chatlog.dto.IntentType;

public class IntentDetector {
    public static IntentType detectIntent(String message) {
        String lower = message.toLowerCase();

        if (lower.contains("ngày") || lower.contains("date")) return IntentType.SEARCH_BY_DATE;
        if (lower.contains("tìm") && lower.contains(":")) return IntentType.QUERY_STRING;
        if (lower.contains("match")) return IntentType.MATCH_QUERY;
        if (lower.contains("term")) return IntentType.TERM_QUERY;
        if (lower.contains("terms")) return IntentType.TERMS_QUERY;

        return IntentType.UNKNOWN;
    }
}
