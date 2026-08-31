package com.fptu.exe.skillswap.architecture;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ModulithAuditEdgeParserTest {

    @Test
    void ignoresLegalCycleDependencyDetailsAndDeduplicatesEdges() {
        String message = """
                - Cycle detected: Slice blog -> Slice booking -> Slice blog
                  1. Dependencies of Slice blog
                    - Constructor <com.example.BlogListener.<init>(com.example.booking.port.PublicQuery)> has parameter of type <com.example.booking.port.PublicQuery> in (BlogListener.java:12)
                - Module 'blog' depends on non-exposed type com.example.booking.service.InternalPolicy within module 'booking'!
                - Module 'blog' depends on non-exposed type com.example.booking.service.InternalPolicy within module 'booking'!
                """;

        Set<ModulithArchitectureAuditTest.AuditEdge> edges =
                ModulithArchitectureAuditTest.parseNonExposedEdges(List.of(message));

        assertThat(edges).containsExactly(new ModulithArchitectureAuditTest.AuditEdge(
                "blog",
                "booking",
                "com.example.booking.service.InternalPolicy",
                "module-boundary",
                "0"
        ));
    }

    @Test
    void associatesSourceLocationWhenModulithIncludesOne() {
        String message = """
                - Module 'blog' depends on non-exposed type com.example.booking.service.InternalPolicy within module 'booking'!
                - Method <com.example.blog.BlogService.read()> calls method <com.example.booking.service.InternalPolicy.check()> in (BlogService.java:42)
                """;

        Set<ModulithArchitectureAuditTest.AuditEdge> edges =
                ModulithArchitectureAuditTest.parseNonExposedEdges(List.of(message));

        assertThat(edges.iterator().next().sourceFile()).isEqualTo("BlogService.java");
        assertThat(edges.iterator().next().sourceLine()).isEqualTo("42");
    }
}
