package com.example.chatlog.utils;

import java.util.List;

public class SchemaHint {

    public static String login() {
        return """
        Login
        Index pattern: {index}.
        - @timestamp (date)
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
        - event.action (e.g., "allow", "deny", "drop")
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
        - destination.ip
        - destination.port
        - event.action (e.g., "allow", "deny", "drop")
        - log.level (e.g., "information", "warning")
        - message
        Default time filter: @timestamp >= NOW() - {hours} HOURS unless specified.
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
                Warning()
        );
    }
}
