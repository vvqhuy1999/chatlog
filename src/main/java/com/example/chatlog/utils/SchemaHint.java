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
        - source.mac (keyword, MAC address of source)
        - source.port (long, source port number)
        - source.domain (keyword, source domain/hostname)
        - source.geo.country_name (keyword, country of source IP)
        - destination.mac (keyword, MAC address of destination)
        - destination.user.name (keyword, destination user name)
        - destination.domain (keyword, destination domain/hostname)
        - destination.geo.country_name (keyword, country of destination IP)
        - observer.name (keyword, Fortigate device name)
        - observer.serial_number (keyword, Fortigate serial number)
        - observer.ingress.interface.name (keyword, ingress interface name)
        - observer.egress.interface.name (keyword, egress interface name)
        - user.name (keyword, general user field, not source/destination specific)
        - network.protocol (keyword, network protocol: TCP/UDP/ICMP)
        - network.transport (keyword, transport protocol: tcp/udp)
        - network.direction (keyword, traffic direction: inbound/outbound/internal)
        - event.type (keyword, event type: connection/denied/configuration)
        - event.category (keyword, event category group)
        - event.duration (long, event duration in milliseconds)
        - log.syslog.severity.code (long, syslog severity code)
        - log.syslog.facility.code (long, syslog facility code)
        - fortinet.firewall.action (keyword, firewall action: allow/deny)
        - fortinet.firewall.type (keyword, log type: traffic/event/utm)
        - fortinet.firewall.subtype (keyword, log subtype: webfilter/forward/virus)
        - fortinet.firewall.ruleid (keyword, rule ID number)
        - fortinet.firewall.policyid (keyword, policy ID number)
        - fortinet.firewall.shapingpolicyname (keyword, shaping policy name)
        - fortinet.firewall.trandisp (keyword, NAT translation type: snat/dnat/noop)
        - fortinet.firewall.srcintfrole (keyword, source interface role: lan/wan/dmz)
        - fortinet.firewall.dstintfrole (keyword, destination interface role: lan/wan/dmz)
        - fortinet.firewall.crlevel (keyword, IPS risk level: "low", "medium", "high", "critical")
        - fortinet.firewall.crscore (long, IPS risk score)
        - fortinet.firewall.sessionid (keyword, session ID)
        - fortinet.firewall.osname (keyword, endpoint OS name)
        - fortinet.firewall.devtype (keyword, endpoint device type)
        - fortinet.firewall.vd (keyword, VDOM name)
        - fortinet.firewall.attack (keyword, attack signature name)
        - fortinet.firewall.attackid (keyword, attack signature ID)
        - rule.category (keyword, rule category: firewall/p2p/web)
        
        IMPORTANT FIELD MAPPINGS (Vietnamese → English):
        - "tổ chức", "organization", "công ty" → use "destination.as.organization.name"
        - "người dùng", "user" → use "source.user.name"
        - "địa chỉ IP", "IP address" → use "source.ip" or "destination.ip"
        - "hành động", "action" → use "event.action"
        - "bytes", "dung lượng", "traffic" → use "network.bytes"
        - "packets", "gói tin" → use "network.packets"
        - "mức rủi ro", "risk level", "crlevel" → use "fortinet.firewall.crlevel"
        - "tấn công", "attack", "signature" → use "fortinet.firewall.attack"

        GEOGRAPHIC & DIRECTION MAPPINGS (CRITICAL):
        - "Việt Nam", "Vietnam", "VN" → use "Vietnam" (exact match)
        - "nước ngoài", "foreign", "international" → use must_not with source/destination country
        - "từ Việt Nam ra nước ngoài" → source.geo.country_name: "Vietnam" AND must_not destination.geo.country_name: "Vietnam"
        - "outbound", "ra ngoài", "đi ra" → use "network.direction": "outbound"
        - "inbound", "vào trong", "đi vào" → use "network.direction": "inbound"
        - "internal", "nội bộ", "trong mạng" → use "network.direction": "internal"
        - "source country" → use "source.geo.country_name"
        - "destination country" → use "destination.geo.country_name"

        FIREWALL ACTION MAPPINGS (CRITICAL):
        - "chặn", "block", "deny", "từ chối" → use "fortinet.firewall.action": "deny"
        - "cho phép", "allow", "accept", "thông qua" → use "fortinet.firewall.action": "allow"
        - "rule chặn nhiều nhất" → filter by action: "deny" + terms agg on "rule.name"
        - "rule cho phép nhiều nhất" → filter by action: "allow" + terms agg on "rule.name"
        - "rule name" → use "rule.name" (NOT fortinet.firewall.ruleid)
        - "policy ID" → use "fortinet.firewall.policyid"
        - "rule ID" → use "fortinet.firewall.ruleid"

        QUERY STRUCTURE BEST PRACTICES:
        - Use "filter" instead of "must" for exact matches and ranges for better performance
        - For time ranges, prefer "gte": "now-24h" over absolute timestamps when possible
        - For aggregations with sorting, use proper order syntax: "order": {"agg_name": "desc"}
        - Example structure: {"bool": {"filter": [{"term": {...}}, {"range": {...}}]}}
        - For terms aggregation, check if field supports aggregation
        - If unsure about field type, use simple field name without .keyword
        - Example: use "source.user.name" not "source.user.name.keyword"
        
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
        
        2. GEOGRAPHIC OUTBOUND TRAFFIC (CRITICAL EXAMPLE):
        Question: "Trong 7 ngày qua, các kết nối outbound từ Việt Nam ra nước ngoài (không phải Việt Nam) là gì?"
        CORRECT Query Structure:
        {
          "query": {
            "bool": {
              "must": [
                { "term": { "network.direction": "outbound" } },
                { "term": { "source.geo.country_name": "Vietnam" } }
              ],
              "must_not": [
                { "term": { "destination.geo.country_name": "Vietnam" } }
              ],
              "filter": [
                { "range": { "@timestamp": { "gte": "now-7d" } } }
              ]
            }
          },
          "size": 10
        }
        
        IMPORTANT GEOGRAPHIC RULES:
        - "từ Việt Nam ra nước ngoài" = outbound + source: Vietnam + must_not destination: Vietnam
        - "vào Việt Nam từ nước ngoài" = inbound + destination: Vietnam + must_not source: Vietnam  
        - "nội bộ Việt Nam" = internal + source: Vietnam + destination: Vietnam
        - ALWAYS use must_not for exclusion, NOT conflicting filters
        
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

  /**
   * Trả về examples về geographic và direction analysis
   */
  public static String getGeographicExamples() {
    return """
        GEOGRAPHIC & DIRECTION ANALYSIS EXAMPLES:
        
        1. Outbound traffic from Vietnam to foreign countries:
        Question: "Trong 7 ngày qua, các kết nối outbound từ Việt Nam ra nước ngoài (không phải Việt Nam) là gì?"
        CORRECT Query Structure:
        {
          "query": {
            "bool": {
              "must": [
                { "term": { "network.direction": "outbound" } },
                { "term": { "source.geo.country_name": "Vietnam" } }
              ],
              "must_not": [
                { "term": { "destination.geo.country_name": "Vietnam" } }
              ],
              "filter": [
                { "range": { "@timestamp": { "gte": "now-7d" } } }
              ]
            }
          },
          "size": 10
        }
        
        2. Inbound traffic to Vietnam from foreign countries:
        Question: "Traffic vào Việt Nam từ nước ngoài trong 24 giờ qua"
        CORRECT Query Structure:
        {
          "query": {
            "bool": {
              "must": [
                { "term": { "network.direction": "inbound" } },
                { "term": { "destination.geo.country_name": "Vietnam" } }
              ],
              "must_not": [
                { "term": { "source.geo.country_name": "Vietnam" } }
              ],
              "filter": [
                { "range": { "@timestamp": { "gte": "now-24h" } } }
              ]
            }
          },
          "size": 10
        }
        
        3. Internal Vietnam traffic:
        Question: "Traffic nội bộ trong Việt Nam hôm nay"
        CORRECT Query Structure:
        {
          "query": {
            "bool": {
              "must": [
                { "term": { "network.direction": "internal" } },
                { "term": { "source.geo.country_name": "Vietnam" } },
                { "term": { "destination.geo.country_name": "Vietnam" } }
              ],
              "filter": [
                { "range": { "@timestamp": { "gte": "now-24h" } } }
              ]
            }
          },
          "size": 10
        }
        
        CRITICAL GEOGRAPHIC RULES:
        - ALWAYS use "Vietnam" (not "Việt Nam") in Elasticsearch queries
        - "từ X ra nước ngoài" = source: X + must_not destination: X
        - "vào X từ nước ngoài" = destination: X + must_not source: X  
        - "nội bộ X" = source: X + destination: X
        - Use must_not for exclusion, NOT conflicting positive filters
        - Combine with network.direction when mentioned (outbound/inbound/internal)
        """;
  }

  /**
   * Trả về examples về firewall rule analysis
   */
  public static String getFirewallRuleExamples() {
    return """
        FIREWALL RULE ANALYSIS EXAMPLES:
        
        1. Top blocking rules in last 24 hours:
        Question: "Những rule nào chặn nhiều nhất trong 24 giờ qua?"
        CORRECT Query Structure:
        {
          "query": {
            "bool": {
              "filter": [
                { "term": { "fortinet.firewall.action": "deny" } },
                { "range": { "@timestamp": { "gte": "now-24h" } } }
              ]
            }
          },
          "aggs": {
            "top_rules": {
              "terms": { "field": "rule.name", "size": 10 }
            }
          },
          "size": 0
        }
        
        2. Top allowing rules by traffic volume:
        Question: "Rule nào cho phép traffic nhiều nhất hôm nay?"
        CORRECT Query Structure:
        {
          "query": {
            "bool": {
              "filter": [
                { "term": { "fortinet.firewall.action": "allow" } },
                { "range": { "@timestamp": { "gte": "now-24h" } } }
              ]
            }
          },
          "aggs": {
            "rules_by_traffic": {
              "terms": { "field": "rule.name", "size": 10, "order": { "total_bytes": "desc" } },
              "aggs": {
                "total_bytes": { "sum": { "field": "network.bytes" } }
              }
            }
          },
          "size": 0
        }
        
        3. Rules with most connections:
        Question: "Rule nào có nhiều connection nhất trong tuần qua?"
        CORRECT Query Structure:
        {
          "query": {
            "bool": {
              "filter": [
                { "range": { "@timestamp": { "gte": "now-7d" } } }
              ]
            }
          },
          "aggs": {
            "rules_by_connections": {
              "terms": { "field": "rule.name", "size": 10 }
            }
          },
          "size": 0
        }
        
        CRITICAL FIREWALL RULE RULES:
        - "chặn nhiều nhất" = filter by action: "deny" + terms agg (default sort by doc_count desc)
        - "cho phép nhiều nhất" = filter by action: "allow" + terms agg
        - Use "rule.name" for rule names, NOT "fortinet.firewall.ruleid"
        - For "nhiều nhất" questions, default terms aggregation sorts by count automatically
        - Don't create complex nested aggregations unless specifically needed
        - Use simple terms agg: {"terms": {"field": "rule.name", "size": 10}}
        """;
  }

  /**
   * Trả về examples về counting và statistical queries
   */
  public static String getCountingExamples() {
    return """
        COUNTING & STATISTICAL QUERY EXAMPLES:
        
        1. Count total logs in a time period:
        Question: "Count total logs today"
        CORRECT Query Structure:
        {
          "query": {
            "range": {
              "@timestamp": {
                "gte": "2025-09-16T00:00:00.000+07:00",
                "lte": "2025-09-16T23:59:59.999+07:00"
              }
            }
          },
          "aggs": {
            "total_logs": {
              "value_count": {
                "field": "@timestamp"
              }
            }
          },
          "size": 0
        }
        
        2. Count logs by user:
        Question: "Tổng có bao nhiêu log ghi nhận từ người dùng TuNM trong ngày hôm nay?"
        CORRECT Query Structure:
        {
          "query": {
            "bool": {
              "filter": [
                { "term": { "source.user.name": "TuNM" } },
                { "range": { "@timestamp": { "gte": "now-24h" } } }
              ]
            }
          },
          "aggs": {
            "user_log_count": {
              "value_count": {
                "field": "@timestamp"
              }
            }
          },
          "size": 0
        }
        
        3. Daily statistics with date histogram:
        Question: "Thống kê số log theo ngày trong 1 tuần qua"
        CORRECT Query Structure:
        {
          "query": {
            "range": {
              "@timestamp": {
                "gte": "now-7d"
              }
            }
          },
          "aggs": {
            "logs_per_day": {
              "date_histogram": {
                "field": "@timestamp",
                "calendar_interval": "day"
              }
            }
          },
          "size": 0
        }
        
        4. Hourly statistics:
        Question: "Thống kê theo giờ trong ngày hôm nay"
        CORRECT Query Structure:
        {
          "query": {
            "range": {
              "@timestamp": {
                "gte": "now-24h"
              }
            }
          },
          "aggs": {
            "logs_per_hour": {
              "date_histogram": {
                "field": "@timestamp",
                "calendar_interval": "hour"
              }
            }
          },
          "size": 0
        }
        
        CRITICAL COUNTING RULES:
        - "count", "tổng", "bao nhiêu", "số lượng" ALWAYS require "aggs" with "value_count"
        - ALWAYS set "size": 0 for counting queries (we only want aggregation results)
        - Use "value_count" on "@timestamp" field for counting total documents
        - For time-based statistics, use "date_histogram" with "calendar_interval"
        - For grouping and counting, combine "terms" agg with default doc_count sorting
        - NEVER return just a query without aggregation for counting questions
        """;
  }
}