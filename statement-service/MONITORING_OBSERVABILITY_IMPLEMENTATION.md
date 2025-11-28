# Monitoring & Observability Implementation Guide

This document provides all code changes needed to implement comprehensive monitoring and observability for the Statement Service.

---

## Table of Contents
1. [Custom Metrics](#1-custom-metrics)
2. [Distributed Tracing](#2-distributed-tracing)
3. [Structured Logging](#3-structured-logging)
4. [Alerting Rules](#4-alerting-rules)
5. [Docker Compose Setup](#5-docker-compose-setup)

---

## 1. Custom Metrics

### 1.1 Add Dependencies to pom.xml

Add these dependencies in the `<dependencies>` section:

```xml
<!-- Micrometer Prometheus Registry -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### 1.2 Create MetricsConfig.java

Create new file: `src/main/java/com/example/statementservice/config/MetricsConfig.java`

```java
package com.example.statementservice.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
            .commonTags(
                "application", "statement-service",
                "environment", System.getenv().getOrDefault("ENVIRONMENT", "development")
            );
    }

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}
```

### 1.3 Create StatementMetrics.java

Create new file: `src/main/java/com/example/statementservice/metrics/StatementMetrics.java`

```java
package com.example.statementservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class StatementMetrics {

    private final MeterRegistry registry;
    private final Counter uploadSuccessCounter;
    private final Counter downloadSuccessCounter;
    private final Counter linkGenerationCounter;
    private final DistributionSummary fileSizeDistribution;
    private final Timer uploadDurationTimer;
    private final Timer downloadDurationTimer;

    public StatementMetrics(MeterRegistry registry) {
        this.registry = registry;
        
        this.uploadSuccessCounter = Counter.builder("statement.upload.success")
            .description("Number of successful statement uploads")
            .register(registry);

        this.downloadSuccessCounter = Counter.builder("statement.download.success")
            .description("Number of successful statement downloads")
            .register(registry);

        this.linkGenerationCounter = Counter.builder("statement.link.generated")
            .description("Number of signed links generated")
            .register(registry);

        this.fileSizeDistribution = DistributionSummary.builder("statement.file.size")
            .description("Distribution of statement file sizes in bytes")
            .baseUnit("bytes")
            .register(registry);

        this.uploadDurationTimer = Timer.builder("statement.upload.duration")
            .description("Time taken to upload and process statements")
            .register(registry);

        this.downloadDurationTimer = Timer.builder("statement.download.duration")
            .description("Time taken to download statements")
            .register(registry);
    }

    public void recordUploadSuccess() {
        uploadSuccessCounter.increment();
    }

    public void recordUploadFailure(String reason) {
        Counter.builder("statement.upload.failure")
            .tag("reason", reason)
            .register(registry)
            .increment();
    }

    public void recordDownloadSuccess() {
        downloadSuccessCounter.increment();
    }

    public void recordDownloadFailure(String reason) {
        Counter.builder("statement.download.failure")
            .tag("reason", reason)
            .register(registry)
            .increment();
    }

    public void recordLinkGeneration() {
        linkGenerationCounter.increment();
    }

    public void recordFileSize(long sizeInBytes) {
        fileSizeDistribution.record(sizeInBytes);
    }

    public Timer.Sample startUploadTimer() {
        return Timer.start(registry);
    }

    public void recordUploadDuration(Timer.Sample sample) {
        sample.stop(uploadDurationTimer);
    }

    public Timer.Sample startDownloadTimer() {
        return Timer.start(registry);
    }

    public void recordDownloadDuration(Timer.Sample sample) {
        sample.stop(downloadDurationTimer);
    }
}
```

### 1.4 Update application.yml

Add Prometheus endpoint configuration:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /api/v1/statements/actuator
  endpoint:
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles-histogram:
        http.server.requests: true
        statement.upload.duration: true
        statement.download.duration: true
    tags:
      application: statement-service
```

---

## 2. Distributed Tracing

### 2.1 Add Tracing Dependencies to pom.xml

```xml
<!-- Micrometer Tracing -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>

<!-- Zipkin Reporter -->
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

### 2.2 Update application.yml for Tracing

```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # Sample 100% of requests (adjust for production)
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}

spring:
  application:
    name: statement-service
```

### 2.3 Create TracingAspect.java

Create new file: `src/main/java/com/example/statementservice/aspect/TracingAspect.java`

```java
package com.example.statementservice.aspect;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class TracingAspect {

    private final Tracer tracer;

    @Around("execution(* com.example.statementservice.service.*.upload*(..))")
    public Object traceUploadOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        Span span = tracer.nextSpan().name("upload-operation").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            span.tag("operation", "upload");
            span.tag("service", "statement-service");
            return joinPoint.proceed();
        } catch (Throwable t) {
            span.error(t);
            throw t;
        } finally {
            span.end();
        }
    }

    @Around("execution(* com.example.statementservice.service.*.download*(..))")
    public Object traceDownloadOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        Span span = tracer.nextSpan().name("download-operation").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            span.tag("operation", "download");
            span.tag("service", "statement-service");
            return joinPoint.proceed();
        } catch (Throwable t) {
            span.error(t);
            throw t;
        } finally {
            span.end();
        }
    }
}
```

---

## 3. Structured Logging

### 3.1 Add Logback Dependencies to pom.xml

```xml
<!-- Logstash Logback Encoder for JSON logging -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>8.0</version>
</dependency>
```

### 3.2 Create logback-spring.xml

Create new file: `src/main/resources/logback-spring.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    
    <springProperty scope="context" name="applicationName" source="spring.application.name" defaultValue="statement-service"/>
    
    <!-- Console Appender with JSON format -->
    <appender name="CONSOLE_JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"application":"${applicationName}"}</customFields>
            <includeMdcKeyName>correlationId</includeMdcKeyName>
            <includeMdcKeyName>userId</includeMdcKeyName>
            <includeMdcKeyName>statementId</includeMdcKeyName>
            <includeMdcKeyName>operation</includeMdcKeyName>
        </encoder>
    </appender>

    <!-- File Appender with JSON format -->
    <appender name="FILE_JSON" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/statement-service.json</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/statement-service.%d{yyyy-MM-dd}.json.gz</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"application":"${applicationName}"}</customFields>
        </encoder>
    </appender>

    <!-- Root Logger -->
    <root level="INFO">
        <appender-ref ref="CONSOLE_JSON"/>
        <appender-ref ref="FILE_JSON"/>
    </root>

    <!-- Development Profile - Human Readable -->
    <springProfile name="dev,local">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%X{correlationId}] - %msg%n</pattern>
            </encoder>
        </appender>
        
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>
</configuration>
```

### 3.3 Create CorrelationIdFilter.java

Create new file: `src/main/java/com/example/statementservice/filter/CorrelationIdFilter.java`

```java
package com.example.statementservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        
        try {
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }
            
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
            
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

---

## 4. Alerting Rules

### 4.1 Create prometheus-alerts.yml

Create new file: `monitoring/prometheus-alerts.yml`

```yaml
groups:
  - name: statement_service_alerts
    interval: 30s
    rules:
      # High Error Rate
      - alert: HighUploadErrorRate
        expr: |
          rate(statement_upload_failure_total[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
          service: statement-service
        annotations:
          summary: "High upload error rate detected"
          description: "Upload error rate is {{ $value }} errors/sec for the last 5 minutes"

      - alert: HighDownloadErrorRate
        expr: |
          rate(statement_download_failure_total[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
          service: statement-service
        annotations:
          summary: "High download error rate detected"
          description: "Download error rate is {{ $value }} errors/sec for the last 5 minutes"

      # Slow Response Times
      - alert: SlowUploadDuration
        expr: |
          histogram_quantile(0.95, rate(statement_upload_duration_seconds_bucket[5m])) > 10
        for: 5m
        labels:
          severity: warning
          service: statement-service
        annotations:
          summary: "Slow upload operations detected"
          description: "95th percentile upload duration is {{ $value }}s"

      - alert: SlowDownloadDuration
        expr: |
          histogram_quantile(0.95, rate(statement_download_duration_seconds_bucket[5m])) > 5
        for: 5m
        labels:
          severity: warning
          service: statement-service
        annotations:
          summary: "Slow download operations detected"
          description: "95th percentile download duration is {{ $value }}s"

      # Service Health
      - alert: ServiceDown
        expr: up{job="statement-service"} == 0
        for: 1m
        labels:
          severity: critical
          service: statement-service
        annotations:
          summary: "Statement service is down"
          description: "Statement service has been down for more than 1 minute"

      - alert: HighMemoryUsage
        expr: |
          (process_resident_memory_bytes{job="statement-service"} / 1024 / 1024) > 1024
        for: 5m
        labels:
          severity: warning
          service: statement-service
        annotations:
          summary: "High memory usage detected"
          description: "Memory usage is {{ $value }}MB"

      # Database Connection Issues
      - alert: DatabaseConnectionPoolExhausted
        expr: |
          hikaricp_connections_active{job="statement-service"} >= hikaricp_connections_max{job="statement-service"}
        for: 2m
        labels:
          severity: critical
          service: statement-service
        annotations:
          summary: "Database connection pool exhausted"
          description: "All database connections are in use"

      # File Storage Issues
      - alert: LargeFileUploads
        expr: |
          histogram_quantile(0.95, rate(statement_file_size_bytes_bucket[5m])) > 10485760
        for: 5m
        labels:
          severity: info
          service: statement-service
        annotations:
          summary: "Large file uploads detected"
          description: "95th percentile file size is {{ $value }} bytes (>10MB)"
```

### 4.2 Create Grafana Dashboard JSON

Create new file: `monitoring/grafana-dashboard.json`

```json
{
  "dashboard": {
    "title": "Statement Service Monitoring",
    "panels": [
      {
        "title": "Upload Success Rate",
        "targets": [
          {
            "expr": "rate(statement_upload_success_total[5m])",
            "legendFormat": "Success Rate"
          }
        ],
        "type": "graph"
      },
      {
        "title": "Download Success Rate",
        "targets": [
          {
            "expr": "rate(statement_download_success_total[5m])",
            "legendFormat": "Success Rate"
          }
        ],
        "type": "graph"
      },
      {
        "title": "Upload Duration (p95)",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(statement_upload_duration_seconds_bucket[5m]))",
            "legendFormat": "p95"
          }
        ],
        "type": "graph"
      },
      {
        "title": "File Size Distribution",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(statement_file_size_bytes_bucket[5m]))",
            "legendFormat": "p95"
          }
        ],
        "type": "graph"
      }
    ]
  }
}
```

---

## 5. Docker Compose Setup

### 5.1 Update docker-compose.yml

Add monitoring services to `docker/docker-compose.yml`:

```yaml
version: '3.8'
services:
  db:
    image: postgres:15
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: statementdb
    ports:
      - "5432:5432"
    volumes:
      - db-data:/var/lib/postgresql/data

  app:
    build:
      context: ..
      dockerfile: docker/Dockerfile
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/statementdb
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_PROFILES_ACTIVE: docker
      ZIPKIN_ENDPOINT: http://zipkin:9411/api/v2/spans
    ports:
      - "8080:8080"
    depends_on:
      - db
      - prometheus
      - zipkin
    volumes:
      - ./files:/data/files

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
      - ./monitoring/prometheus-alerts.yml:/etc/prometheus/alerts.yml
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_USERS_ALLOW_SIGN_UP=false
    volumes:
      - grafana-data:/var/lib/grafana
      - ./monitoring/grafana-dashboard.json:/etc/grafana/provisioning/dashboards/statement-service.json
    depends_on:
      - prometheus

  zipkin:
    image: openzipkin/zipkin:latest
    ports:
      - "9411:9411"
    environment:
      - STORAGE_TYPE=mem

  alertmanager:
    image: prom/alertmanager:latest
    ports:
      - "9093:9093"
    volumes:
      - ./monitoring/alertmanager.yml:/etc/alertmanager/alertmanager.yml
    command:
      - '--config.file=/etc/alertmanager/alertmanager.yml'

volumes:
  db-data:
  prometheus-data:
  grafana-data:
```

### 5.2 Create prometheus.yml

Create new file: `monitoring/prometheus.yml`

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

alerting:
  alertmanagers:
    - static_configs:
        - targets:
            - alertmanager:9093

rule_files:
  - /etc/prometheus/alerts.yml

scrape_configs:
  - job_name: 'statement-service'
    metrics_path: '/api/v1/statements/actuator/prometheus'
    static_configs:
      - targets: ['app:8080']
        labels:
          application: 'statement-service'
          environment: 'docker'
```

### 5.3 Create alertmanager.yml

Create new file: `monitoring/alertmanager.yml`

```yaml
global:
  resolve_timeout: 5m

route:
  group_by: ['alertname', 'service']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 12h
  receiver: 'default'

receivers:
  - name: 'default'
    webhook_configs:
      - url: 'http://localhost:5001/webhook'
        send_resolved: true
```

---

## Summary

This implementation provides:

1. **Custom Metrics**: Track uploads, downloads, file sizes, and operation durations
2. **Distributed Tracing**: Zipkin integration for request tracing across services
3. **Structured Logging**: JSON-formatted logs with correlation IDs
4. **Alerting Rules**: Prometheus alerts for errors, performance, and health

### Quick Start

1. Add all dependencies to `pom.xml`
2. Create all new Java files in their respective packages
3. Create monitoring configuration files
4. Update `docker-compose.yml`
5. Run: `cd docker && docker-compose up --build`

### Access Points

- Application: http://localhost:8080
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)
- Zipkin: http://localhost:9411
- Metrics: http://localhost:8080/api/v1/statements/actuator/prometheus