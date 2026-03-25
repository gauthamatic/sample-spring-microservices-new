# System Observability using ELK — Learning Notes

---

## Overall Architecture

```
Spring Microservice
      |
      | writes logs
      v
  Log File (disk)
      |
      | ships log lines
      v
   Filebeat  (lightweight agent)
      |
      | forwards to
      v
   Logstash  (parse, enrich, transform)
      |
      | pushes structured docs
      v
 Elasticsearch  (stores & indexes logs)
      |
      | queries
      v
   Kibana  (search, filter, visualize)
```

---

## Phase 1 — Logging Fundamentals (Application Level)

### 1.1 What is SLF4J?

SLF4J (Simple Logging Facade for Java) is NOT a logging framework.
It is an abstraction layer — your code talks to SLF4J, and SLF4J delegates to the real implementation.

```
Your Code
    |
    | uses
    v
 SLF4J API  (org.slf4j.Logger, LoggerFactory)
    |
    | delegates to (binding on classpath)
    v
 Logback  ← default in Spring Boot
 (or Log4j2, JUL, etc.)
```

Why this matters: you can swap Logback for Log4j2 without changing any application code.

#### How it's used in this project

```java
// 1. Declare a logger (once per class)
private static final Logger LOGGER = LoggerFactory.getLogger(EmployeeController.class);

// 2. Log at appropriate levels
LOGGER.info("Employee add: {}", employee);    // normal flow
LOGGER.debug("Fetching employee: id={}", id); // detailed dev info
LOGGER.error("Failed to find employee", e);   // failures
```

#### {} placeholders — why they matter

```java
// BAD — string always built, even if DEBUG is disabled
LOGGER.debug("Employee: " + employee.toString());

// GOOD — string only built if DEBUG is enabled
LOGGER.debug("Employee: {}", employee);
```

#### Log Levels (low → high severity)

```
TRACE → DEBUG → INFO → WARN → ERROR
  |        |       |      |       |
verbose  detail  normal  warn  failures
  (dev)   (dev)  (prod)        (prod)
```

Setting level to INFO means TRACE and DEBUG are ignored.

#### Alternatives to SLF4J

| Option              | Type           | Notes                                      |
|---------------------|----------------|--------------------------------------------|
| SLF4J + Logback     | Facade + impl  | Default in Spring Boot. Most widely used   |
| SLF4J + Log4j2      | Facade + impl  | Faster async logging, more config options  |
| Log4j2 directly     | Impl only      | Ties code to Log4j2 — not recommended      |
| java.util.logging   | Built-in JDK   | No extra deps, limited features            |
| Lombok @Slf4j       | Code gen       | Auto-generates LOGGER field, still SLF4J   |

---

### 1.2 Where are logs written?

By default in Spring Boot — **stdout (console) only**. No file is written.

To write to a file, configure in `application.yml`:
```yaml
logging:
  file:
    name: logs/employee-service.log
```

```
Spring Boot App
      |
      |--- stdout (console)         ← default, always on
      |
      |--- logs/employee-service.log  ← only if configured
```

---

### 1.3 Why JSON format?

#### Plain text log (hard to parse)
```
2026-03-23 14:48:37  INFO 12345 --- [exec-1] EmployeeController : Employee add: Employee [id=1, orgId=1...]
```

#### JSON log (machine-friendly)
```json
{
  "timestamp": "2026-03-23T14:48:37.807+05:30",
  "level": "INFO",
  "service": "employee-service",
  "thread": "exec-1",
  "logger": "EmployeeController",
  "message": "Employee add",
  "employeeId": 1,
  "organizationId": 1
}
```

Benefits of JSON:
- Every field is queryable in Elasticsearch
- Consistent structure across all microservices
- No fragile text parsing rules in Logstash
- Extra context (requestId, userId) can be added as fields

---

### 1.4 What is Logstash?

Logstash is a data processing pipeline with 3 stages:

```
         INPUT                  FILTER                  OUTPUT
    ┌─────────────┐        ┌─────────────┐         ┌─────────────┐
    │  Filebeat   │───────▶│  Parse JSON │────────▶│Elasticsearch│
    │  log file   │        │  Add fields │         │             │
    │  TCP/Kafka  │        │  Drop noise │         │  stdout     │
    └─────────────┘        └─────────────┘         └─────────────┘
```

- Input: where logs come from (Filebeat, file, TCP, Kafka...)
- Filter: transform data (parse, enrich, clean)
- Output: where processed logs go (Elasticsearch, file, stdout)

If logs are already JSON → filter stage is minimal (just parse JSON, no grok needed).

---

### 1.5 Structured Logging — JSON log format

Tool: `logstash-logback-encoder` — Logback encoder that outputs JSON.

#### Dependency (pom.xml)
```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

#### logback-spring.xml config
```xml
<appender name="JSON_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/employee-service.log</file>
    <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
</appender>
```

`LogstashEncoder` automatically includes: timestamp, level, logger, thread, message, stack traces.

---

### 1.6 Key-Value Logging (attaching context to logs)

Use SLF4J's MDC (Mapped Diagnostic Context) to attach fields to every log in a request:

```java
MDC.put("employeeId", String.valueOf(id));
LOGGER.info("Fetching employee");   // log will include employeeId field
MDC.clear();
```

Or use structured arguments inline (with logstash-logback-encoder):
```java
import static net.logstash.logback.argument.StructuredArguments.kv;

LOGGER.info("Employee add", kv("employeeId", employee.getId()), kv("dept", employee.getDepartmentId()));
```

Output in JSON:
```json
{ "message": "Employee add", "employeeId": 1, "dept": 2 }
```

---

### 1.7 Standard Log Fields

Every log entry should consistently have these fields:

| Field       | Description                        | Example                        |
|-------------|------------------------------------|--------------------------------|
| timestamp   | When the event happened            | 2026-03-23T14:48:37.807+05:30  |
| level       | Severity                           | INFO, ERROR, DEBUG             |
| service     | Which microservice                 | employee-service               |
| traceId     | Distributed trace ID               | abc123 (from Micrometer)       |
| spanId      | Span within a trace                | def456                         |
| thread      | Thread name                        | nio-8080-exec-1                |
| logger      | Class that logged                  | EmployeeController             |
| message     | Human-readable description         | Employee add                   |

`traceId` and `spanId` are automatically added by Micrometer Tracing (already in this project's pom).

---

### 1.8 Error Code Logging

Define standard error codes so every failure maps to a known, searchable code.

```java
public enum ErrorCode {
    EMPLOYEE_NOT_FOUND("EMP-001"),
    INVALID_DEPARTMENT("EMP-002"),
    INTERNAL_ERROR("EMP-500");

    private final String code;
    ErrorCode(String code) { this.code = code; }
    public String getCode() { return code; }
}
```

Log with the error code as a structured field:
```java
LOGGER.error("Employee not found", kv("errorCode", ErrorCode.EMPLOYEE_NOT_FOUND.getCode()), kv("employeeId", id));
```

JSON output:
```json
{ "level": "ERROR", "message": "Employee not found", "errorCode": "EMP-001", "employeeId": 42 }
```

Now in Kibana you can filter: `errorCode: EMP-001` across all services.

---

### 1.9 Exception Logging

#### Stack trace logging
```java
try {
    return repository.findById(id);
} catch (NoSuchElementException e) {
    LOGGER.error("Employee not found", kv("errorCode", "EMP-001"), kv("employeeId", id), e);
    throw e;
}
```

Passing the exception `e` as the last argument tells SLF4J to include the full stack trace in the log.

#### Exception context — include request details
```java
LOGGER.error("Failed to add employee",
    kv("errorCode", "EMP-500"),
    kv("organizationId", employee.getOrganizationId()),
    kv("departmentId", employee.getDepartmentId()),
    e);
```

#### Error classification

```
Exception Types
      |
      |── Business Errors  → known, expected failures
      |       e.g. EmployeeNotFoundException, InvalidDepartmentException
      |       → log at WARN or ERROR with specific error code
      |
      └── System Errors    → unexpected, infrastructure failures
              e.g. NullPointerException, DB connection failure
              → log at ERROR with stack trace + EMP-500
```

---

*Notes last updated: Phase 1 complete*
