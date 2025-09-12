package com.example.chatlog.utils;

import java.util.List;

public class SchemaHint {

    public static String login() {
        return """
        Login
        Index pattern: {index}.
        - @timestamp
        - source.user.name (keyword)
        - source.ip (ip)
        - destination.ip (ip)
        - event.action (keyword, e.g., "login", "logout")
        - event.outcome (keyword: success/failure)
        - event.module (should be "fortinet")
        - event.dataset (should be "fortinet_fortigate.log")
        - message (text)
        
        Default time filter: @timestamp >= NOW() - {hours} HOURS unless the question specifies otherwise.
        When the question is about successful logins, filter with event.action like *login* and event.outcome == success.
        When counting or grouping, return meaningful columns (e.g., source.user.name, source.ip, count, last_seen).
        """;
    }

    public static String findUser() {
        return """
        Find User
        Index pattern: {index}.
        Default time filter: @timestamp >= NOW() - {hours} HOURS unless the question specifies otherwise.
        Always use source.user.name to query the user.
        When counting or grouping, return meaningful columns (e.g., source.user.name, source.ip, count, last_seen).
        """;
    }

    public static String failedLogin() {
        return """
        Failed Login
        Index pattern: {index}.
        Use ECS fields:
        - @timestamp
        - source.user.name
        - source.ip
        - destination.ip
        - event.action ("login")
        - event.outcome ("failure")
        
        Default time filter: @timestamp >= NOW() - {hours} HOURS unless the question specifies otherwise.
        Always filter with event.action like *login* AND event.outcome == failure.
        """;
    }

    public static String connections() {
        return """
        Connections
        Index pattern: {index}.
        Use ECS fields:
        - @timestamp
        - source.ip
        - destination.ip
        - network.transport (tcp/udp)
        - event.action (e.g., "connection_start", "connection_end")
        
        Default time filter: @timestamp >= NOW() - {hours} HOURS unless specified.
        When grouping, return source.ip, destination.ip, count, and last_seen.
        """;
    }

    public static String aggregationByIP() {
        return """
        Aggregation by IP
        Index pattern: {index}.
        Use ECS fields:
        - @timestamp
        - source.ip
        - destination.ip
        - event.action
        - event.outcome
        
        Default time filter: @timestamp >= NOW() - {hours} HOURS unless specified.
        When aggregating, group by source.ip and destination.ip.
        Return meaningful fields (ip, count, last_seen).
        """;
    }

    public static String firewallEvents() {
        return """
        Firewall events
        Index pattern: {index}.
        Use ECS fields:
        - @timestamp
        - source.ip
        - destination.ip
        - destination.port
        - event.action (e.g.,"login", "accept", "close", "server-rst", "timeout", "ssl-anomaly", "logged-off", "ssh_login", "Health Check")
        - rule.name
        - event.outcome
        
        Default time filter: @timestamp >= NOW() - {hours} HOURS unless specified.
        When aggregating, group by event.action or rule.name.
        """;
    }

    public static String Warning() {
        return """
        Warning
        Index pattern: {index}.
        Use ECS fields:
        - @timestamp
        - source.ip
        - source.user.name
        - destination.ip
        - destination.port
        - event.action (e.g., "accept", "close", "server-rst", "timeout", "ssl-anomaly", "logged-off", "ssh_login", "Health Check")
        - log.level (e.g., "information", "warning", "notice", "alert")
        - message
        Default time filter: @timestamp >= NOW() - {hours} HOURS unless specified.
        """;

    }

        public static String webTraffic() {
            return """
            Web Traffic
            Index pattern: {index}.
            Use ECS fields:
            - @timestamp
            - source.ip
            - source.user.name
            - destination.ip
            - destination.port (80, 443)
            - http.request.method (GET, POST, PUT, DELETE)
            - http.response.status_code
            - url.full
            - user_agent.original
            
            Default time filter: @timestamp >= NOW() - {hours} HOURS unless specified.
            When aggregating, group by http.response.status_code or url.domain.
            """;
        }


        public static String systemErrors() {
            return """
            System Errors
            Index pattern: {index}.
            Use ECS fields:
            - @timestamp
            - host.name
            - log.level (error, critical, fatal)
            - process.name
            - process.pid
            - message
        
            Default time filter: @timestamp >= NOW() - {hours} HOURS unless specified.
            Always filter log.level in (error, critical, fatal).
        """;
        }

    public static String dateTimeWithOffset() {
        return """
        DateTime with Offset
        Use the same format as stored in logs for @timestamp.
        Example: "@timestamp": "2025-09-03T09:45:25.000+07:00"
        
        Rules:
        - Always include milliseconds (.SSS).
        - Always include timezone offset (+HH:mm).
        - Make sure both gte and lte use this format in range queries.
        - Do not truncate the offset or remove milliseconds.
        """;
    }


    public static List<String> allSchemas() {
        return List.of(
            login(),
            findUser(),
            failedLogin(),
            connections(),
            aggregationByIP(),
            firewallEvents(),
            Warning(),
            webTraffic(),
            systemErrors(),
            dateTimeWithOffset()
        );
    }
}
