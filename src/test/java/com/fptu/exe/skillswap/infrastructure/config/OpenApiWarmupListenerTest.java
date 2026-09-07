package com.fptu.exe.skillswap.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.webmvc.api.MultipleOpenApiWebMvcResource;
import org.springdoc.webmvc.api.OpenApiWebMvcResource;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OpenApiWarmupListenerTest {

    @Test
    @DisplayName("Should skip warmup when enabled is false")
    void whenDisabled_shouldSkipWarmup() throws Exception {
        @SuppressWarnings("unchecked")
        ObjectProvider<MultipleOpenApiWebMvcResource> multipleProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<OpenApiWebMvcResource> defaultProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<List<GroupedOpenApi>> groupsProvider = mock(ObjectProvider.class);

        OpenApiWarmupListener listener = new OpenApiWarmupListener(
                multipleProvider, defaultProvider, groupsProvider,
                false, false, 0, 0
        );

        listener.onApplicationReady();

        verifyNoInteractions(multipleProvider, defaultProvider, groupsProvider);
    }

    @Test
    @DisplayName("Should handle null providers safely without throwing exceptions")
    void whenProvidersNull_shouldHandleSafely() {
        @SuppressWarnings("unchecked")
        ObjectProvider<MultipleOpenApiWebMvcResource> multipleProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<OpenApiWebMvcResource> defaultProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<List<GroupedOpenApi>> groupsProvider = mock(ObjectProvider.class);

        when(multipleProvider.getIfAvailable()).thenReturn(null);
        when(defaultProvider.getIfAvailable()).thenReturn(null);
        when(groupsProvider.getIfAvailable()).thenReturn(null);

        OpenApiWarmupListener listener = new OpenApiWarmupListener(
                multipleProvider, defaultProvider, groupsProvider,
                true, false, 0, 0
        );

        assertDoesNotThrow(listener::performWarmup);
    }

    @Test
    @DisplayName("Should warm up available groups dynamically in sorted order when 00-all is not present")
    void whenEnabledSync_without00All_shouldWarmUpDynamicallyInSortedOrder() throws Exception {
        @SuppressWarnings("unchecked")
        ObjectProvider<MultipleOpenApiWebMvcResource> multipleProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<OpenApiWebMvcResource> defaultProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<List<GroupedOpenApi>> groupsProvider = mock(ObjectProvider.class);

        MultipleOpenApiWebMvcResource multipleResource = mock(MultipleOpenApiWebMvcResource.class);
        OpenApiWebMvcResource defaultResource = mock(OpenApiWebMvcResource.class);

        GroupedOpenApi group2 = GroupedOpenApi.builder().group("02-mentor").pathsToMatch("/api/me/mentor/**").build();
        GroupedOpenApi group1 = GroupedOpenApi.builder().group("01-identity").pathsToMatch("/api/auth/**").build();
        GroupedOpenApi group3 = GroupedOpenApi.builder().group("03-booking").pathsToMatch("/api/bookings/**").build();

        when(multipleProvider.getIfAvailable()).thenReturn(multipleResource);
        when(defaultProvider.getIfAvailable()).thenReturn(defaultResource);
        // deliberately pass in unordered: 02-mentor, 01-identity, 03-booking
        when(groupsProvider.getIfAvailable()).thenReturn(List.of(group2, group1, group3));

        OpenApiWarmupListener listener = new OpenApiWarmupListener(
                multipleProvider, defaultProvider, groupsProvider,
                true, false, 0, 0
        );

        listener.performWarmup();

        InOrder inOrder = inOrder(multipleResource, defaultResource);
        inOrder.verify(multipleResource).openapiJson(any(), eq("/v3/api-docs"), eq("01-identity"), eq(Locale.ENGLISH));
        inOrder.verify(multipleResource).openapiJson(any(), eq("/v3/api-docs"), eq("02-mentor"), eq(Locale.ENGLISH));
        inOrder.verify(multipleResource).openapiJson(any(), eq("/v3/api-docs"), eq("03-booking"), eq(Locale.ENGLISH));
        inOrder.verify(defaultResource).openapiJson(any(), eq("/v3/api-docs"), eq(Locale.ENGLISH));
    }

    @Test
    @DisplayName("Should warm up 00-all naturally first when present")
    void whenEnabledSync_with00AllPresent_shouldWarmUp00AllNaturallyFirst() throws Exception {
        @SuppressWarnings("unchecked")
        ObjectProvider<MultipleOpenApiWebMvcResource> multipleProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<OpenApiWebMvcResource> defaultProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<List<GroupedOpenApi>> groupsProvider = mock(ObjectProvider.class);

        MultipleOpenApiWebMvcResource multipleResource = mock(MultipleOpenApiWebMvcResource.class);

        GroupedOpenApi group1 = GroupedOpenApi.builder().group("01-identity").pathsToMatch("/api/auth/**").build();
        GroupedOpenApi group0 = GroupedOpenApi.builder().group("00-all").pathsToMatch("/api/**").build();

        when(multipleProvider.getIfAvailable()).thenReturn(multipleResource);
        when(defaultProvider.getIfAvailable()).thenReturn(null);
        when(groupsProvider.getIfAvailable()).thenReturn(List.of(group1, group0));

        OpenApiWarmupListener listener = new OpenApiWarmupListener(
                multipleProvider, defaultProvider, groupsProvider,
                true, false, 0, 0
        );

        listener.performWarmup();

        InOrder inOrder = inOrder(multipleResource);
        inOrder.verify(multipleResource).openapiJson(any(), eq("/v3/api-docs"), eq("00-all"), eq(Locale.ENGLISH));
        inOrder.verify(multipleResource).openapiJson(any(), eq("/v3/api-docs"), eq("01-identity"), eq(Locale.ENGLISH));
    }

    @Test
    @DisplayName("Should start background thread and complete asynchronously when async is true")
    void whenEnabledAsync_shouldStartBackgroundThread() throws Exception {
        @SuppressWarnings("unchecked")
        ObjectProvider<MultipleOpenApiWebMvcResource> multipleProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<OpenApiWebMvcResource> defaultProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<List<GroupedOpenApi>> groupsProvider = mock(ObjectProvider.class);

        MultipleOpenApiWebMvcResource multipleResource = mock(MultipleOpenApiWebMvcResource.class);
        when(multipleProvider.getIfAvailable()).thenReturn(multipleResource);
        when(defaultProvider.getIfAvailable()).thenReturn(null);
        when(groupsProvider.getIfAvailable()).thenReturn(List.of(
                GroupedOpenApi.builder().group("01-identity").pathsToMatch("/api/auth/**").build()
        ));

        OpenApiWarmupListener listener = new OpenApiWarmupListener(
                multipleProvider, defaultProvider, groupsProvider,
                true, true, 10, 0
        );

        assertDoesNotThrow(listener::onApplicationReady);

        // Give async thread a brief moment to run
        Thread.sleep(150);

        verify(multipleResource, atLeastOnce()).openapiJson(any(), eq("/v3/api-docs"), eq("01-identity"), eq(Locale.ENGLISH));
    }
}
