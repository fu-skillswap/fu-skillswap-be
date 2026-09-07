package com.fptu.exe.skillswap.infrastructure.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.webmvc.api.MultipleOpenApiWebMvcResource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Eagerly pre-warms OpenAPI models and schema definitions during application startup.
 *
 * <p>Springdoc generates OpenAPI models on-demand by default. For large applications with
 * 150+ endpoints and hundreds of DTOs, first-request generation can take 20-30 seconds,
 * triggering Cloudflare 504 Gateway Timeout on VPS deployments with limited resources.
 *
 * <p>To prevent blocking Spring Boot readiness probes and container health checks on deployment,
 * this listener runs asynchronously by default on a background daemon thread, warming up `00-all`
 * first to populate Jackson and Springdoc schema reflection caches, with gentle yielding between groups.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true", matchIfMissing = true)
public class OpenApiWarmupListener {

    private final ObjectProvider<MultipleOpenApiWebMvcResource> multipleOpenApiResourceProvider;
    private final ObjectProvider<org.springdoc.webmvc.api.OpenApiWebMvcResource> defaultOpenApiResourceProvider;
    private final ObjectProvider<List<GroupedOpenApi>> groupedOpenApisProvider;
    private final boolean enabled;
    private final boolean async;
    private final long initialDelayMs;
    private final long groupDelayMs;

    public OpenApiWarmupListener(
            ObjectProvider<MultipleOpenApiWebMvcResource> multipleOpenApiResourceProvider,
            ObjectProvider<org.springdoc.webmvc.api.OpenApiWebMvcResource> defaultOpenApiResourceProvider,
            ObjectProvider<List<GroupedOpenApi>> groupedOpenApisProvider,
            @Value("${application.openapi.warmup.enabled:true}") boolean enabled,
            @Value("${application.openapi.warmup.async:true}") boolean async,
            @Value("${application.openapi.warmup.initial-delay-ms:1000}") long initialDelayMs,
            @Value("${application.openapi.warmup.group-delay-ms:100}") long groupDelayMs
    ) {
        this.multipleOpenApiResourceProvider = multipleOpenApiResourceProvider;
        this.defaultOpenApiResourceProvider = defaultOpenApiResourceProvider;
        this.groupedOpenApisProvider = groupedOpenApisProvider;
        this.enabled = enabled;
        this.async = async;
        this.initialDelayMs = initialDelayMs;
        this.groupDelayMs = groupDelayMs;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!enabled) {
            log.info("OpenAPI documentation warmup is disabled.");
            return;
        }

        if (async) {
            log.info("Scheduling OpenAPI documentation warmup asynchronously in background thread (initial delay: {} ms)...", initialDelayMs);
            Thread warmupThread = new Thread(this::performWarmupSafe, "openapi-warmup");
            warmupThread.setDaemon(true);
            warmupThread.start();
        } else {
            performWarmup();
        }
    }

    private void performWarmupSafe() {
        try {
            if (initialDelayMs > 0) {
                Thread.sleep(initialDelayMs);
            }
            performWarmup();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("OpenAPI documentation warmup thread was interrupted.");
        } catch (Throwable t) {
            log.warn("OpenAPI documentation warmup encountered an unexpected error: {}", t.getMessage(), t);
        }
    }

    public void performWarmup() {
        MultipleOpenApiWebMvcResource multipleResource = multipleOpenApiResourceProvider.getIfAvailable();
        org.springdoc.webmvc.api.OpenApiWebMvcResource defaultResource = defaultOpenApiResourceProvider.getIfAvailable();
        List<GroupedOpenApi> groups = groupedOpenApisProvider.getIfAvailable();

        if (multipleResource == null && defaultResource == null) {
            return;
        }

        long t0 = System.currentTimeMillis();
        int groupCount = (groups != null) ? groups.size() : 0;
        log.info("Starting eager OpenAPI documentation warmup (target groups: {}, async: {})...", groupCount, async);
        HttpServletRequest dummyRequest = createDummyRequest();
        Locale targetLocale = Locale.ENGLISH;

        if (multipleResource != null && groups != null) {
            // Sort to ensure '00-all' is warmed up FIRST, priming all DTO schemas for subsequent groups
            List<GroupedOpenApi> sortedGroups = groups.stream()
                    .sorted((g1, g2) -> {
                        if ("00-all".equals(g1.getGroup())) return -1;
                        if ("00-all".equals(g2.getGroup())) return 1;
                        return g1.getGroup().compareTo(g2.getGroup());
                    })
                    .toList();

            for (GroupedOpenApi group : sortedGroups) {
                try {
                    long groupT0 = System.currentTimeMillis();
                    multipleResource.openapiJson(dummyRequest, "/v3/api-docs", group.getGroup(), targetLocale);
                    log.info("Warmed up OpenAPI group '{}' [locale: {}] in {} ms",
                            group.getGroup(), targetLocale.toLanguageTag(), System.currentTimeMillis() - groupT0);
                } catch (Exception e) {
                    log.warn("Failed to warm up OpenAPI group '{}' [locale: {}]: {}",
                            group.getGroup(), targetLocale.toLanguageTag(), e.getMessage());
                }

                if (groupDelayMs > 0) {
                    try {
                        Thread.sleep(groupDelayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("OpenAPI documentation warmup interrupted between groups.");
                        return;
                    }
                }
            }
        }

        if (defaultResource != null) {
            try {
                long defaultT0 = System.currentTimeMillis();
                defaultResource.openapiJson(dummyRequest, "/v3/api-docs", targetLocale);
                log.info("Warmed up default OpenAPI resource [locale: {}] in {} ms",
                        targetLocale.toLanguageTag(), System.currentTimeMillis() - defaultT0);
            } catch (Exception e) {
                log.warn("Failed to warm up default OpenAPI resource [locale: {}]: {}",
                        targetLocale.toLanguageTag(), e.getMessage());
            }
        }

        log.info("OpenAPI documentation warmup completed in {} ms total", System.currentTimeMillis() - t0);
    }

    private static HttpServletRequest createDummyRequest() {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getScheme" -> "http";
                    case "getServerName" -> "localhost";
                    case "getServerPort" -> 8080;
                    case "getContextPath" -> "";
                    case "getRequestURI" -> "/v3/api-docs";
                    case "getRequestURL" -> new StringBuffer("http://localhost:8080/v3/api-docs");
                    case "getHeader" -> null;
                    case "getHeaders" -> Collections.emptyEnumeration();
                    case "getHeaderNames" -> Collections.emptyEnumeration();
                    case "getLocale" -> Locale.ENGLISH;
                    case "getLocales" -> Collections.enumeration(List.of(Locale.ENGLISH));
                    case "toString" -> "OpenApiWarmupDummyHttpServletRequest";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> args != null && args.length > 0 && proxy == args[0];
                    default -> defaultValueFor(method.getReturnType());
                }
        );
    }

    private static Object defaultValueFor(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0.0f;
        }
        if (returnType == double.class) {
            return 0.0d;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }
}
