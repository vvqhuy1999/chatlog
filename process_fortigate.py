#!/usr/bin/env python3
"""
Process fortigate_queries_full.json to add scenario descriptions and enhance keywords.
"""
import json
import re

def analyze_and_enhance_entry(entry):
    """Analyze a query entry and add scenario + enhanced keywords."""
    question = entry.get("question", "")
    keywords = entry.get("keywords", [])
    query = entry.get("query", {})

    # Create scenario based on question pattern analysis
    scenario = create_scenario(question, query)

    # Enhance keywords with additional terms
    enhanced_keywords = enhance_keywords(question, keywords, query)

    # Create new entry with scenario
    return {
        "question": question,
        "keywords": enhanced_keywords,
        "scenario": scenario,
        "query": query
    }

def create_scenario(question, query):
    """Create an English scenario description based on the question and query."""
    question_lower = question.lower()

    # Security Monitoring scenarios
    if any(keyword in question_lower for keyword in ["bị chặn", "deny", "blocked"]):
        if "ip nguồn" in question_lower or "source ip" in question_lower:
            return "Security monitoring - Identify top blocked source IPs to detect potential threats, brute force attacks, or persistent malicious actors"
        elif "rule" in question_lower or "policy" in question_lower:
            return "Security monitoring - Analyze which firewall rules are blocking the most traffic to optimize security policies"
        elif "port" in question_lower:
            return "Security monitoring - Track blocked port activities to identify unauthorized access attempts and potential security breaches"
        else:
            return "Security monitoring - Review blocked traffic patterns to identify security threats and policy violations"

    # IPS/Threat Detection scenarios
    if any(keyword in question_lower for keyword in ["ips", "rủi ro", "risk", "attack", "tấn công", "threat"]):
        if "cao" in question_lower or "high" in question_lower or "critical" in question_lower:
            return "Threat detection - List high-risk IPS events to prioritize security incident response and protect critical assets"
        elif "signature" in question_lower or "chữ ký" in question_lower:
            return "Threat detection - Identify IPS signature matches to understand attack patterns and update security defenses"
        else:
            return "Threat detection - Monitor intrusion prevention system events to detect and respond to security threats"

    # Authentication/Login scenarios
    if any(keyword in question_lower for keyword in ["đăng nhập", "login", "authentication", "xác thực"]):
        if "thất bại" in question_lower or "fail" in question_lower:
            return "Security audit - Track failed login attempts to detect brute force attacks, credential stuffing, and unauthorized access attempts"
        elif "admin" in question_lower or "quản trị" in question_lower:
            return "Security audit - Monitor administrative access to ensure compliance and detect potential insider threats"
        else:
            return "Security audit - Review authentication events to ensure authorized access and maintain compliance with security policies"

    # Traffic/Network Analysis scenarios
    if any(keyword in question_lower for keyword in ["bytes", "lưu lượng", "traffic", "băng thông", "bandwidth"]):
        if "nhiều nhất" in question_lower or "top" in question_lower:
            return "Network performance - Identify top traffic consumers to optimize bandwidth allocation and detect anomalies"
        elif "shaping" in question_lower or "qos" in question_lower:
            return "Network performance - Analyze traffic shaping effectiveness to optimize QoS policies and bandwidth management"
        else:
            return "Network performance - Monitor network traffic patterns to ensure optimal performance and detect unusual activity"

    # Geo-location scenarios
    if any(keyword in question_lower for keyword in ["nước ngoài", "foreign", "country", "quốc gia", "địa lý"]):
        if "outbound" in question_lower or "đi ra" in question_lower:
            return "Compliance monitoring - Track outbound connections to foreign countries for regulatory compliance and data sovereignty"
        elif "inbound" in question_lower or "đi vào" in question_lower:
            return "Security monitoring - Monitor inbound connections from foreign sources to detect potential threats and unauthorized access"
        else:
            return "Compliance monitoring - Analyze geographic traffic patterns for compliance, security, and business intelligence"

    # Application scenarios
    if any(keyword in question_lower for keyword in ["ứng dụng", "application", "app"]):
        if "nhiều nhất" in question_lower or "top" in question_lower:
            return "Application monitoring - Identify most-used applications to optimize network resources and enforce acceptable use policies"
        elif "chặn" in question_lower or "block" in question_lower:
            return "Application control - Review blocked applications to enforce security policies and prevent shadow IT"
        else:
            return "Application monitoring - Track application usage for visibility, compliance, and network optimization"

    # VPN scenarios
    if any(keyword in question_lower for keyword in ["vpn", "ssl-vpn", "ipsec"]):
        if "tunnel" in question_lower:
            return "Remote access - Monitor VPN tunnel statistics to ensure reliable remote connectivity and troubleshoot issues"
        elif "user" in question_lower or "người dùng" in question_lower:
            return "Remote access - Track VPN user activity for security auditing and capacity planning"
        else:
            return "Remote access - Monitor VPN connections to ensure secure remote access and troubleshoot connectivity issues"

    # DNS scenarios
    if any(keyword in question_lower for keyword in ["dns", "domain", "tên miền"]):
        return "Security monitoring - Analyze DNS queries to detect malware communication, data exfiltration, and policy violations"

    # Web/URL scenarios
    if any(keyword in question_lower for keyword in ["url", "web", "http", "https"]):
        if "blocked" in question_lower or "chặn" in question_lower:
            return "Web filtering - Review blocked URLs to enforce acceptable use policies and prevent access to malicious sites"
        else:
            return "Web filtering - Monitor web access patterns for security, compliance, and productivity management"

    # Port-specific scenarios
    if "rdp" in question_lower or "3389" in question_lower:
        return "Security monitoring - Monitor RDP access attempts to detect unauthorized remote desktop connections and potential breaches"
    elif "ssh" in question_lower or "22" in question_lower:
        return "Security monitoring - Track SSH connections to detect unauthorized access and ensure secure remote administration"
    elif "smtp" in question_lower or "25" in question_lower or "email" in question_lower:
        return "Email security - Monitor SMTP traffic to detect spam, phishing attempts, and email-borne threats"

    # Time-based analysis scenarios
    if any(keyword in question_lower for keyword in ["timeline", "theo thời gian", "xu hướng", "trend"]):
        return "Trend analysis - Visualize traffic patterns over time to identify trends, anomalies, and capacity planning needs"

    # Policy/Rule scenarios
    if any(keyword in question_lower for keyword in ["policy", "rule", "chính sách", "quy tắc"]):
        if "so sánh" in question_lower or "compar" in question_lower:
            return "Policy optimization - Compare policy effectiveness to optimize firewall rules and improve security posture"
        elif "hiệu quả" in question_lower or "effective" in question_lower:
            return "Policy optimization - Evaluate policy effectiveness to ensure rules are achieving their intended security objectives"
        else:
            return "Policy management - Analyze firewall policies to optimize rules, improve security, and reduce complexity"

    # Session scenarios
    if any(keyword in question_lower for keyword in ["session", "phiên", "kết nối", "connection"]):
        if "long" in question_lower or "dài" in question_lower:
            return "Network monitoring - Identify long-running sessions to detect persistent connections and potential security issues"
        else:
            return "Network monitoring - Track session data to understand connection patterns and troubleshoot network issues"

    # Device/Interface scenarios
    if any(keyword in question_lower for keyword in ["interface", "giao diện", "wan", "lan"]):
        return "Network monitoring - Analyze traffic by interface to optimize routing and identify network bottlenecks"

    # Protocol scenarios
    if any(keyword in question_lower for keyword in ["protocol", "giao thức", "tcp", "udp", "icmp"]):
        return "Network analysis - Monitor protocol usage to understand traffic composition and detect protocol-based attacks"

    # User scenarios
    if any(keyword in question_lower for keyword in ["user", "người dùng", "tài khoản"]):
        return "User activity - Track user behavior for security auditing, compliance, and investigation of security incidents"

    # Default scenario
    return "Network security - Monitor and analyze firewall logs for security threats, compliance, and operational insights"

def enhance_keywords(question, existing_keywords, query):
    """Enhance keywords with additional related terms in both languages."""
    question_lower = question.lower()
    enhanced = list(existing_keywords)  # Start with existing keywords

    # Add keywords based on question content
    keyword_mappings = {
        # IP related
        "ip nguồn": ["nguồn", "source", "địa chỉ IP nguồn", "source IP address", "src IP"],
        "ip đích": ["đích", "destination", "địa chỉ IP đích", "destination IP address", "dst IP"],
        "ip": ["địa chỉ IP", "IP address", "địa chỉ", "address"],

        # Blocking/Deny related
        "chặn": ["bị chặn", "blocked", "cấm", "denied", "từ chối", "reject", "ngăn chặn", "prevent"],
        "deny": ["denied", "bị từ chối", "block", "chặn", "reject"],
        "block": ["blocked", "chặn", "deny", "cấm"],

        # Traffic/Bytes related
        "bytes": ["lưu lượng", "traffic", "dữ liệu", "data", "băng thông", "bandwidth", "dung lượng", "volume"],
        "lưu lượng": ["traffic", "bytes", "băng thông", "bandwidth", "data flow", "dòng dữ liệu"],
        "bandwidth": ["băng thông", "traffic", "lưu lượng", "throughput"],

        # Time related
        "24 giờ": ["24h", "một ngày", "1 day", "24 hours", "ngày qua", "last day"],
        "1 giờ": ["1h", "hour", "giờ qua", "last hour"],
        "7 ngày": ["7d", "tuần", "week", "7 days", "tuần qua", "last week"],
        "48 giờ": ["48h", "2 days", "2 ngày", "hai ngày"],

        # Security/Risk related
        "rủi ro": ["risk", "nguy hiểm", "danger", "threat", "mối đe dọa", "위험", "rủi ro cao", "high risk"],
        "ips": ["intrusion prevention", "phòng chống xâm nhập", "security", "bảo mật", "threat prevention"],
        "attack": ["tấn công", "intrusion", "xâm nhập", "exploit", "khai thác"],
        "threat": ["mối đe dọa", "위협", "nguy cơ", "위험", "attack", "tấn công"],

        # Authentication related
        "đăng nhập": ["login", "authentication", "xác thực", "sign in", "truy cập", "access"],
        "thất bại": ["failure", "failed", "unsuccessful", "không thành công", "lỗi", "error"],
        "admin": ["administrator", "quản trị viên", "quản trị", "root", "superuser"],

        # Network direction
        "outbound": ["đi ra", "ra ngoài", "egress", "xuất", "outgoing"],
        "inbound": ["đi vào", "vào trong", "ingress", "nhập", "incoming"],

        # Geographic
        "nước ngoài": ["foreign", "international", "quốc tế", "overseas", "external"],
        "việt nam": ["vietnam", "domestic", "trong nước", "local"],

        # Application related
        "ứng dụng": ["application", "app", "phần mềm", "software", "service", "dịch vụ"],

        # Policy/Rule related
        "policy": ["chính sách", "rule", "quy tắc", "firewall rule", "quy định"],
        "rule": ["quy tắc", "policy", "chính sách", "luật", "regulation"],

        # Ranking/Statistics
        "nhiều nhất": ["top", "most", "highest", "cao nhất", "maximum", "tối đa", "xếp hạng", "ranking"],
        "top": ["nhiều nhất", "hàng đầu", "cao nhất", "highest", "leading"],

        # Protocols
        "rdp": ["remote desktop", "3389", "truy cập từ xa", "desktop remoto"],
        "ssh": ["secure shell", "22", "terminal", "remote access"],
        "vpn": ["virtual private network", "mạng riêng ảo", "tunnel", "kết nối bảo mật"],
        "dns": ["domain name system", "tên miền", "name resolution", "phân giải tên"],

        # Port related
        "port": ["cổng", "service port", "network port", "TCP/UDP port"],
        "wan": ["wide area network", "mạng diện rộng", "internet", "external"],
        "lan": ["local area network", "mạng cục bộ", "internal", "nội bộ"],

        # Actions
        "accept": ["allow", "permit", "cho phép", "chấp nhận", "pass"],
        "allow": ["permit", "accept", "cho phép", "pass"],

        # Session related
        "session": ["phiên", "kết nối", "connection", "phiên làm việc", "session connection"],
        "connection": ["kết nối", "session", "phiên", "liên kết", "link"],

        # Analysis types
        "thống kê": ["statistics", "stat", "metrics", "số liệu", "phân tích"],
        "so sánh": ["compare", "comparison", "đối chiếu", "versus", "vs"],
        "timeline": ["theo thời gian", "time series", "xu hướng", "trend", "biểu đồ thời gian"],
    }

    # Add contextual keywords based on question analysis
    for key_phrase, additional_keywords in keyword_mappings.items():
        if key_phrase in question_lower:
            for kw in additional_keywords:
                if kw.lower() not in [k.lower() for k in enhanced]:
                    enhanced.append(kw)

    # Add query-specific keywords based on Elasticsearch query structure
    query_str = json.dumps(query).lower()

    # Check for specific fields in query
    if "fortinet.firewall.action" in query_str:
        for kw in ["action", "hành động", "firewall action"]:
            if kw not in enhanced:
                enhanced.append(kw)

    if "source.geo" in query_str or "destination.geo" in query_str:
        for kw in ["địa lý", "geography", "location", "vị trí", "country", "quốc gia"]:
            if kw not in enhanced:
                enhanced.append(kw)

    if "network.bytes" in query_str:
        for kw in ["network bytes", "packet size", "kích thước gói tin"]:
            if kw not in enhanced:
                enhanced.append(kw)

    if "event.outcome" in query_str:
        for kw in ["outcome", "kết quả", "result", "status", "trạng thái"]:
            if kw not in enhanced:
                enhanced.append(kw)

    if "user" in query_str:
        for kw in ["user activity", "hoạt động người dùng", "account", "tài khoản"]:
            if kw not in enhanced:
                enhanced.append(kw)

    if "date_histogram" in query_str or "histogram" in query_str:
        for kw in ["timeline", "theo thời gian", "time series", "xu hướng", "trend"]:
            if kw not in enhanced:
                enhanced.append(kw)

    if "terms" in query_str and "aggs" in query_str:
        for kw in ["aggregation", "tổng hợp", "statistics", "thống kê", "grouping", "nhóm"]:
            if kw not in enhanced:
                enhanced.append(kw)

    return enhanced

def main():
    input_file = "/home/user/chatlog/src/main/resources/fortigate_queries_full.json"
    output_file = "/home/user/chatlog/src/main/resources/fortigate_queries_full.json"

    print(f"Reading {input_file}...")
    with open(input_file, 'r', encoding='utf-8') as f:
        data = json.load(f)

    print(f"Processing {len(data)} entries...")
    enhanced_data = []

    for i, entry in enumerate(data):
        if (i + 1) % 10 == 0:
            print(f"  Processed {i + 1}/{len(data)} entries...")
        enhanced_entry = analyze_and_enhance_entry(entry)
        enhanced_data.append(enhanced_entry)

    print(f"\nWriting enhanced data to {output_file}...")
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(enhanced_data, f, ensure_ascii=False, indent=2)

    print(f"\n✓ Successfully processed {len(enhanced_data)} entries!")

    # Print sample results
    print("\n" + "="*80)
    print("SAMPLE RESULTS (first 3 entries):")
    print("="*80)
    for i, entry in enumerate(enhanced_data[:3]):
        print(f"\n--- Entry {i+1} ---")
        print(f"Question: {entry['question']}")
        print(f"Scenario: {entry['scenario']}")
        print(f"Keywords ({len(entry['keywords'])}): {', '.join(entry['keywords'][:15])}...")
        if len(entry['keywords']) > 15:
            print(f"           ... and {len(entry['keywords']) - 15} more")

    print("\n" + "="*80)
    print("STATISTICS:")
    print("="*80)
    print(f"Total entries processed: {len(enhanced_data)}")
    print(f"All entries now have 'scenario' field")
    print(f"All entries have enhanced keywords")

    # Count average keywords
    avg_keywords = sum(len(e['keywords']) for e in enhanced_data) / len(enhanced_data)
    print(f"Average keywords per entry: {avg_keywords:.1f}")

if __name__ == "__main__":
    main()
