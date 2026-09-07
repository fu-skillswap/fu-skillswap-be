package com.fptu.exe.skillswap.infrastructure.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.webmvc.api.MultipleOpenApiWebMvcResource;
import org.springframework.beans.factory.ObjectProvider;
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
 * <p>This listener generates and caches all configured {@link GroupedOpenApi} groups
 * (including `00-all`) upon startup, ensuring all subsequent requests return in &lt;10ms.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true", matchIfMissing = true)
public class OpenApiWarmupListener {

    private final ObjectProvider<MultipleOpenApiWebMvcResource> multipleOpenApiResourceProvider;
    private final ObjectProvider<org.springdoc.webmvc.api.OpenApiWebMvcResource> defaultOpenApiResourceProvider;
    private final ObjectProvider<List<GroupedOpenApi>> groupedOpenApisProvider;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        MultipleOpenApiWebMvcResource multipleResource = multipleOpenApiResourceProvider.getIfAvailable();
        org.springdoc.webmvc.api.OpenApiWebMvcResource defaultResource = defaultOpenApiResourceProvider.getIfAvailable();
        List<GroupedOpenApi> groups = groupedOpenApisProvider.getIfAvailable();

        if (multipleResource == null && defaultResource == null) {
            return;
        }

        long t0 = System.currentTimeMillis();
        int groupCount = (groups != null) ? groups.size() : 0;
        log.info("Starting eager OpenAPI documentation warmup for {} groups...", groupCount);
        HttpServletRequest dummyRequest = createDummyRequest();

        List<Locale> targetLocales = java.util.stream.Stream.of(Locale.ENGLISH, Locale.getDefault())
                .distinct()
                .toList();

        if (defaultResource != null) {
            for (Locale locale : targetLocales) {
                try {
                    defaultResource.openapiJson(dummyRequest, "/v3/api-docs", locale);
                    log.info("Warmed up default OpenAPI resource [locale: {}]", locale.toLanguageTag());
                } catch (Exception e) {
                    log.warn("Failed to warm up default OpenAPI resource [locale: {}]: {}", locale.toLanguageTag(), e.getMessage());
                }
            }
        }

        if (multipleResource != null && groups != null) {
            for (GroupedOpenApi group : groups) {
                for (Locale locale : targetLocales) {
                    try {
                        long groupT0 = System.currentTimeMillis();
                        multipleResource.openapiJson(dummyRequest, "/v3/api-docs", group.getGroup(), locale);
                        log.info("Warmed up OpenAPI group '{}' [locale: {}] in {} ms", group.getGroup(), locale.toLanguageTag(), System.currentTimeMillis() - groupT0);
                    } catch (Exception e) {
                        log.warn("Failed to warm up OpenAPI group '{}' [locale: {}]: {}", group.getGroup(), locale.toLanguageTag(), e.getMessage());
                    }
                }
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
                    case "getLocale" -> Locale.getDefault();
                    case "getLocales" -> Collections.enumeration(List.of(Locale.getDefault()));
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
