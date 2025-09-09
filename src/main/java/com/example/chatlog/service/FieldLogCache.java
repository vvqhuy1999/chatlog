package com.example.chatlog.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


public class FieldLogCache {

    private static String cachedFieldLog;

    @Autowired
    private LogApiService logApiService;

    public String getFieldLog() {
        if (cachedFieldLog == null) {
            cachedFieldLog = logApiService.getFieldLog("logs-fortinet_fortigate.log-default*");
        }
        return cachedFieldLog;
    }
}

