package com.example.chatlog.utils;

import java.util.List;

public class SchemaHint {

  /**
   * Schema hint chính cho Fortinet integration theo ECS fields
   * Tương đương với SCHEMA_HINT trong Python
   */
  public static String getSchemaHint() {
    return """
        Index pattern: {index}.
        Use ECS field names typical for Fortinet integration:
        - @timestamp (date)
        - source.user.name (keyword)
        - source.user.roles (keyword, e.g., "Administrator")
        
        IMPORTANT ROLE MAPPINGS:
        - Questions about "admin", "ad", "administrator" should use "Administrator" (capitalized)
        - Always normalize roles: admin/ad/administrator → Administrator
        - Example query: {"term": {"source.user.roles": "Administrator"}}
        
        - source.ip (ip)
        - destination.ip (ip)
        - destination.as.organization.name (keyword, external organization name, e.g., "Google LLC", "Amazon.com", "Microsoft Corporation")
        - destination.as.number (long, ASN number)
        - event.action (keyword, e.g., "login", "logout", "accept", "deny", "close", "server-rst", "client-rst", "dns", "timeout", "ssl-anomaly", "logged-on", "signature", "logged-off", "ssh_login", "Health Check")
        - event.outcome (keyword: success/failure)
        - event.module (should be "fortinet")
        - event.dataset (should be "fortinet_fortigate.log")
        - message (text)
        - destination.port (long)
        - rule.name (keyword, e.g., "TO_INTERNET_SDWAN", "AD_SERVICES", "BLOCK_EXTERNAL_DNS", "AP_CONTROLLER", "SNMP_SERVICE", "TO_AP_CONTROLLER", "PRINT_CONTROLLER_CONNECT", "TO_VMS_CAMERA", "ADMIN_MGMT", "HTTP_HTTPs_SERVICES", "TO_JBSIGN")
        - log.level (keyword, e.g., "info", "error", "information", "warning", "notice", "alert", "warn", "critical")
        - http.request.method (keyword, e.g., "GET", "POST")
        - user_agent.original (text)
        - host.name (keyword)
        - process.name (keyword)
        - process.pid (long)
        - network.bytes (long, for traffic analysis)
        - network.packets (long, for packet count)
        - fortinet.firewall.crlevel (keyword, IPS risk level: "low", "medium", "high", "critical")
        - fortinet.firewall.attack (keyword, attack signature name)
        - fortinet.firewall.attackid (keyword, attack signature ID)
        
        QUERY STRUCTURE BEST PRACTICES:
        - Use "filter" instead of "must" for exact matches and ranges for better performance
        - For time ranges, prefer "gte": "now-24h" over absolute timestamps when possible
        - For aggregations with sorting, use proper order syntax: "order": {"agg_name": "desc"}
        - Example structure: {"bool": {"filter": [{"term": {...}}, {"range": {...}}]}}
        
        Default time filter: @timestamp >= NOW() - {hours} HOURS unless the question specifies otherwise.
        When counting or grouping, return meaningful columns (e.g., user.name, source.user.roles, source.ip, count, last_seen).
        IMPORTANT: Only add event.action/event.outcome filters when explicitly mentioned in the question.
        """;
  }

  /**
   * Chuẩn hóa roles thành format chuẩn
   * Ví dụ: admin, ad, Admin, administrator -> Administrator
   */
  public static String normalizeRole(String role) {
    if (role == null || role.trim().isEmpty()) {
      return role;
    }

    String normalized = role.trim().toLowerCase();

    // Chuẩn hóa các biến thể của Administrator
    switch (normalized) {
      case "admin":
      case "ad":
      case "administrator":
        return "Administrator";
      default:
        // Giữ nguyên format gốc nhưng chuẩn hóa chữ hoa đầu
        return role.trim().substring(0, 1).toUpperCase() +
            role.trim().substring(1).toLowerCase();
    }
  }

  /**
   * Trả về danh sách schema hints (chỉ có một schema duy nhất)
   */
  public static List<String> allSchemas() {
    return List.of(getSchemaHint());
  }

  /**
   * Trả về role normalization rules để sử dụng trong AI prompt
   */
  public static String getRoleNormalizationRules() {
    return """
        ROLE NORMALIZATION RULES:
        - "admin", "ad", "administrator" → ALWAYS use "Administrator" (capitalized)
        - For source.user.roles field, normalize to standard format: "Administrator"
        - Example: {"term": {"source.user.roles": "Administrator"}} not "admin"
        """;
  }

  /**
   * Trả về example query cho admin roles
   */
  public static String getAdminRoleExample() {
    return """
        Question: "hôm ngày 11-09 có roles admin nào vào hệ thống hay ko?"
        Response: {"body":"{\\"query\\":{\\"bool\\":{\\"must\\":[{\\"term\\":{\\"source.user.roles\\":\\"Administrator\\"}},{\\"range\\":{\\"@timestamp\\":{\\"gte\\":\\"2025-09-11T00:00:00.000+07:00\\",\\"lte\\":\\"2025-09-11T23:59:59.999+07:00\\"}}}]}},\\"size\\":10}","query":1}
        """;
  }

  /**
   * Trả về examples về network traffic analysis
   */
  public static String getNetworkTrafficExamples() {
    return """
        NETWORK TRAFFIC ANALYSIS EXAMPLES:
        
        1. Top destinations by bytes from specific source:
        Question: "Từ IP nguồn 10.0.0.25, đích nào nhận nhiều bytes nhất trong 24 giờ qua?"
        Correct Query Structure:
        {
          "query": {
            "bool": {
              "filter": [
                { "term": { "source.ip": "10.0.0.25" } },
                { "range": { "@timestamp": { "gte": "now-24h" } } }
              ]
            }
          },
          "aggs": {
            "by_dst": {
              "terms": { "field": "destination.ip", "size": 10, "order": { "bytes_sum": "desc" } },
              "aggs": {
                "bytes_sum": { "sum": { "field": "network.bytes" } }
              }
            }
          },
          "size": 0
        }
        
        2. Traffic statistics by organization:
        {
          "query": {
            "bool": {
              "filter": [
                { "range": { "@timestamp": { "gte": "now-7d" } } }
              ]
            }
          },
          "aggs": {
            "by_org": {
              "terms": { "field": "destination.as.organization.name", "size": 10, "order": { "total_bytes": "desc" } },
              "aggs": {
                "total_bytes": { "sum": { "field": "network.bytes" } },
                "total_packets": { "sum": { "field": "network.packets" } }
              }
            }
          },
          "size": 0
        }
        
        IMPORTANT PATTERNS:
        - Use "filter" instead of "must" for better performance
        - Use "now-24h", "now-7d" for relative time ranges
        - Aggregation naming: use descriptive names like "by_destination", "total_bytes"
        - Always set "size": 0 for aggregation-only queries
        """;
  }

  /**
   * Trả về examples về IPS security analysis
   */
  public static String getIPSSecurityExamples() {
    return """
        IPS SECURITY ANALYSIS EXAMPLES:
        
        1. High/Critical IPS events in last 24 hours:
        Question: "Liệt kê các phiên có mức rủi ro IPS cao (crlevel = high/critical) trong 1 ngày qua"
        Correct Query Structure:
        {
          "query": {
            "bool": {
              "filter": [
                { "range": { "@timestamp": { "gte": "now-24h" } } },
                { "terms": { "fortinet.firewall.crlevel": ["high", "critical"] } }
              ]
            }
          },
          "sort": [{ "@timestamp": "desc" }]
        }
        
        2. Top attack signatures by count:
        {
          "query": {
            "bool": {
              "filter": [
                { "range": { "@timestamp": { "gte": "now-7d" } } },
                { "exists": { "field": "fortinet.firewall.attack" } }
              ]
            }
          },
          "aggs": {
            "top_attacks": {
              "terms": { "field": "fortinet.firewall.attack", "size": 10 },
              "aggs": {
                "risk_levels": { "terms": { "field": "fortinet.firewall.crlevel" } }
              }
            }
          },
          "size": 0
        }
        
        3. IPS events by risk level distribution:
        {
          "query": {
            "bool": {
              "filter": [
                { "range": { "@timestamp": { "gte": "now-24h" } } },
                { "exists": { "field": "fortinet.firewall.crlevel" } }
              ]
            }
          },
          "aggs": {
            "risk_distribution": {
              "terms": { "field": "fortinet.firewall.crlevel", "size": 10 }
            }
          },
          "size": 0
        }
        
        IPS FIELD MAPPINGS:
        - "mức rủi ro", "risk level", "crlevel" → use "fortinet.firewall.crlevel"
        - "attack", "tấn công", "signature" → use "fortinet.firewall.attack"
        - "attack ID", "signature ID" → use "fortinet.firewall.attackid"
        - For multiple risk levels, use "terms" filter: {"terms": {"fortinet.firewall.crlevel": ["high", "critical"]}}
        """;
  }
}