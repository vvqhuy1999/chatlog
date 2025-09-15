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
        - event.action (keyword, e.g., "login")
        - event.outcome (keyword: success/failure)
        - event.module (should be "fortinet_fortigate"/"system")
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

    public static String aggregationByIP() {
        return """
        Aggregation by IP
        Index pattern: {index}.
        Use ECS fields:
        - @timestamp
        - source.ip
        - destination.ip
        - event.action (e.g.,"login", "accept","deny", "close", "server-rst", "client-rst","dns","", "timeout", "ssl-anomaly","logged-on","signature", "logged-off", "ssh_login", "Health Check")
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
        - event.action (e.g.,"login", "accept","deny", "close", "server-rst", "client-rst","dns","", "timeout", "ssl-anomaly","logged-on","signature", "logged-off", "ssh_login", "Health Check")
        - rule.name (e.g.,"TO_INTERNET_SDWAN", "AD_SERVICES", "BLOCK_EXTERNAL_DNS", "AP_CONTROLLER", "SNMP_SERVICE", "TO_AP_CONTROLLER", "PRINT_CONTROLLER_CONNECT", "TO_VMS_CAMERA", "ADMIN_MGMT","HTTP_HTTPs_SERVICES","TO_JBSIGN")
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
        - event.action (e.g.,"login", "accept","deny", "close", "server-rst", "client-rst","dns","", "timeout", "ssl-anomaly","logged-on","signature", "logged-off", "ssh_login", "Health Check")
        - log.level (e.g.,"info","error", "information", "warning", "notice", "alert", "warn", "critical")
        - message
        Default time filter: @timestamp >= NOW() - {hours} HOURS unless specified.
        """;

    }


        public static String systemErrors() {
            return """
            System Errors
            Index pattern: {index}.
            Use ECS fields:
            - @timestamp
            - host.name
            - log.level (e.g.,"info","error", "information", "warning", "notice", "alert", "warn", "critical")
            - process.name
            - process.pid
            - message
        
            Default time filter: @timestamp >= NOW() - {hours} HOURS unless specified.
            Always filter log.level in (error, critical, fatal).
        """;
        }



    public static List<String> allSchemas() {
        return List.of(
            login(),
            findUser(),
            failedLogin(),
            aggregationByIP(),
            firewallEvents(),
            Warning(),
            systemErrors()
        );
    }
}
