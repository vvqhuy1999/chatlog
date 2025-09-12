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

        public static String webTraffic() {
            return """
            Web Traffic
            Index pattern: {index}.
            Use ECS fields:
            - @timestamp
            - source.ip
            - source.user.name
            - destination.ip
            - destination.port
            - http.request.method (GET)
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
            - log.level (e.g.,"info","error", "information", "warning", "notice", "alert", "warn", "critical")
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
//    public static String jsonStructure() {
//        return """
//    JSON Query Structure (Elasticsearch DSL)
//    ----------------------------------------
//    - Always use valid Elasticsearch JSON syntax.
//    - Ensure all braces `{}` and brackets `[]` are balanced.
//    - Queries must follow this format:
//      {
//        "query": {
//          "bool": {
//            "must": [
//              { ... },   // each condition must be a separate object
//              { ... }
//            ],
//            "must_not": [
//              { ... }   // optional exclusion conditions
//            ]
//          }
//        },
//        "size": N,
//        "sort": [ { "@timestamp": { "order": "desc" } } ],
//        "_source": [ ... ],
//        "aggs": { ... }   // optional aggregation
//      }
//
//    Field handling:
//    - Use "@timestamp" as the date field for range filters.
//    - Time format must be ISO-8601 with timezone, e.g. 2025-09-12T15:45:31.000+07:00.
//    - Alternatively, use relative times like "now-30m/m" and "now/m".
//    - Do not append .keyword to fields automatically.
//    - If unsure about mapping (text vs keyword), use "match" instead of "term".
//
//    MUST / MUST_NOT rules:
//    - "must" must ALWAYS be an array of condition objects:
//      ✅ { "range": { "@timestamp": { "gte": "...", "lte": "..." } } }
//      ✅ { "term": { "source.user.name": "ThuanVD" } }
//    - "must_not" must ALWAYS be an array and declared at the same level as "must":
//      ✅ "must_not": [ { "term": { "http.request.method": "HTTPS" } } ]
//      ❌ Do NOT nest "must_not" inside another "bool" inside "must".
//
//    Aggregations:
//    - "aggs" is optional, example:
//      "aggs": {
//        "user_actions": {
//          "terms": { "field": "event.action", "size": 10 }
//        }
//      }
//
//    Common pitfalls:
//    - Do not mix "term" with text fields.
//    - Always close each object and array properly.
//    - If query returns no logs, verify field type in index mapping.
//    """;
//    }







    public static List<String> allSchemas() {
        return List.of(
            login(),
            findUser(),
            failedLogin(),
            aggregationByIP(),
            firewallEvents(),
            Warning(),
            webTraffic(),
            systemErrors(),
            dateTimeWithOffset()
//                ,jsonStructure()
        );
    }
}
