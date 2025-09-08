package com.example.chatlog.dto;

public enum IntentType {
    SEARCH_BY_DATE,      ////// lấy log theo ngày
    SEARCH_ALL,          // lấy tất cả log trong index
    SEARCH_BY_ID,        // lấy log theo id
    QUERY_STRING,        // tìm bằng query string
    MATCH_QUERY,         // match
    TERM_QUERY,          // term (1 giá trị chính xác)
    TERMS_QUERY,         // terms (nhiều giá trị)
    UNKNOWN
}
