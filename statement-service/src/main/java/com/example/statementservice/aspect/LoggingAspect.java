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

    @Around("controllerPackage() || servicePackage()")
    public Object logAround(ProceedingJoinPoint pjp) throws Throwable {
        String className = pjp.getSignature().getDeclaringTypeName();
        String methodName = pjp.getSignature().getName();
        Object[] args = pjp.getArgs();

        if (log.isInfoEnabled()) {
            log.info("Entering {}.{}({})", className, methodName, formatArgs(args));
        }

        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            long tookMs = (System.nanoTime() - start) / 1_000_000;
            if (log.isInfoEnabled()) {
                log.info("Exiting  {}.{} -> {} ({} ms)", className, methodName, summarizeResult(result), tookMs);
            }
            return result;
        } catch (Throwable ex) {
            long tookMs = (System.nanoTime() - start) / 1_000_000;
            log.warn("Exception in {}.{} after {} ms: {}", className, methodName, tookMs, ex.toString());
            throw ex;
        }
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
            } catch (Exception ignored) {
            }
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

        // Avoid verbose toString on entities; print class and key fields if present
        String simple = ClassUtils.getShortName(arg.getClass());
        try {
            var idField = arg.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            Object id = idField.get(arg);
            return simple + "{id=" + Objects.toString(id) + "}";
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
        }

        // Fallback toString but guard size
        String s = String.valueOf(arg);
        if (s.length() > 300) {
            return simple + "{" + s.substring(0, 300) + "…}";
        }
        return s;
    }

    private String summarizeResult(Object result) {
        if (result == null) return "null";
        if (result instanceof ResponseEntity<?> resp) {
            int status = resp.getStatusCode().value();
            Object body = resp.getBody();
            String bodyType = body == null ? "null" : ClassUtils.getShortName(body.getClass());
            return "ResponseEntity(status=" + status + ", body=" + bodyType + ")";
        }
        if (result instanceof Resource) {
            return "Resource[" + ClassUtils.getShortName(result.getClass()) + "]";
        }
        String s = String.valueOf(result);
        if (s.length() > 200) {
            return ClassUtils.getShortName(result.getClass()) + "{" + s.substring(0, 200) + "…}";
        }
        return s;
    }
}
