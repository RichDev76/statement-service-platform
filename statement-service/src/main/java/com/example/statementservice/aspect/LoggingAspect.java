package com.example.statementservice.aspect;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Aspect
@Component
public class LoggingAspect {

    // Controllers
    @Pointcut("within(com.example.statementservice.controller..*)")
    public void controllerPackage() {}

    // Services
    @Pointcut("within(com.example.statementservice.service..*)")
    public void servicePackage() {}

    // --- Controller methods: log at INFO ---
    @Around("controllerPackage()")
    public Object logController(ProceedingJoinPoint pjp) throws Throwable {
        String className = pjp.getSignature().getDeclaringTypeName();
        String methodName = pjp.getSignature().getName();
        long start = System.nanoTime();

        if (log.isInfoEnabled()) {
            logInfoEntry(className, methodName);
        }

        try {
            Object result = pjp.proceed();
            long tookMs = getTimeTaken(start);

            if (log.isInfoEnabled()) {
                logInfoExit(className, methodName, tookMs);
            }

            if (log.isDebugEnabled()) {
                logDebugExit(className, methodName, result, tookMs);
            }

            return result;
        } catch (Throwable ex) {

            logExceptionWarn(className, methodName, start, ex);
            throw ex;
        }
    }

    @Around("servicePackage()")
    public Object logService(ProceedingJoinPoint pjp) throws Throwable {
        String className = pjp.getSignature().getDeclaringTypeName();
        String methodName = pjp.getSignature().getName();
        long start = System.nanoTime();

        if (log.isDebugEnabled()) {
            logDebugEntry(pjp, className, methodName);
        }

        try {
            Object result = pjp.proceed();
            long tookMs = getTimeTaken(start);

            if (log.isDebugEnabled()) {
                logDebugExit(className, methodName, result, tookMs);
            }

            return result;
        } catch (Throwable ex) {
            logExceptionWarn(className, methodName, start, ex);
            throw ex;
        }
    }

    private long getTimeTaken(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    private String formatArgs(Object[] args) {
        if (args == null || args.length == 0) return "";
        return Arrays.stream(args)
            .map(this::safeToString)
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    }

    private String safeToString(Object arg) {
        if (arg == null) return "null";

        if (arg instanceof MultipartFile file) {
            String originalName = file.getOriginalFilename();
            String contentType = file.getContentType();
            long size = -1L;
            try {
                size = file.getSize();
            } catch (Exception ignored) {}
            return "MultipartFile[name=" + originalName + ", contentType=" + contentType + ", size=" + size + "]";
        }

        if (arg instanceof CharSequence cs) {
            String s = cs.toString();
            if (s.length() > 200) {
                return '"' + s.substring(0, 200) + "…" + '"';
            }
            return '"' + s + '"';
        }

        if (arg instanceof byte[] bytes) {
            return "byte[" + bytes.length + "]";
        }

        if (arg instanceof Resource) {
            return "Resource[" + ClassUtils.getShortName(arg.getClass()) + "]";
        }

        if (arg instanceof Optional<?> opt) {
            return "Optional[" + (opt.isPresent() ? safeToString(opt.get()) : "empty") + "]";
        }

        if (arg.getClass().isArray()) {
            return arg.getClass().getComponentType().getSimpleName() + "[]";
        }

        String simple = ClassUtils.getShortName(arg.getClass());
        try {
            var idField = arg.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            Object id = idField.get(arg);
            return simple + "{id=" + Objects.toString(id) + "}";
        } catch (NoSuchFieldException | IllegalAccessException ignored) {}

        String s = String.valueOf(arg);
        if (s.length() > 300) {
            return simple + "{" + s.substring(0, 300) + "…}";
        }
        return s;
    }

    private String summarizeResult(Object result) {
        switch (result) {
            case null -> {
                return "null";
            }
            case ResponseEntity<?> resp -> {
                int status = resp.getStatusCode().value();
                Object body = resp.getBody();
                String bodyType = body == null ? "null" : ClassUtils.getShortName(body.getClass());
                return "ResponseEntity(status=" + status + ", body=" + bodyType + ")";
            }
            case Resource resource -> {
                return "Resource[" + ClassUtils.getShortName(result.getClass()) + "]";
            }
            default -> {
            }
        }
        String s = String.valueOf(result);
        if (s.length() > 200) {
            return ClassUtils.getShortName(result.getClass()) + "{" + s.substring(0, 200) + "…}";
        }
        return s;
    }
    private void logInfoEntry(String className, String methodName) {
        log.info("Entering {}.{}", className, methodName);
    }

    private void logInfoExit(String className, String methodName, long tookMs) {
        log.info("Exiting  {}.{} [OK] ({} ms)", className, methodName, tookMs);
    }

    private void logDebugEntry(ProceedingJoinPoint pjp, String className, String methodName) {
        log.debug("Entering {}.{}({})", className, methodName, formatArgs(pjp.getArgs()));
    }

    private void logDebugExit(String className, String methodName, Object result, long tookMs) {
        log.debug(
            "Exiting  {}.{} -> {} ({} ms)",
            className,
            methodName,
            summarizeResult(result),
            tookMs);
    }

    private void logExceptionWarn(String className, String methodName, long start, Throwable ex) {
        log.warn("Exception in {}.{} after {} ms: {}", className, methodName, getTimeTaken(start), ex.toString());
    }
}
