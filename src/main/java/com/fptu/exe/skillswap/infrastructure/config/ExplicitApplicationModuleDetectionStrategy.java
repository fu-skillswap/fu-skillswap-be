package com.fptu.exe.skillswap.infrastructure.config;

import org.springframework.modulith.core.ApplicationModuleDetectionStrategy;
import org.springframework.modulith.core.JavaPackage;

import java.util.stream.Stream;

/**
 * Detects the explicitly declared business modules below {@code modules.*}.
 *
 * <p>The project keeps feature code in a grouping package named {@code modules}.
 * Spring Modulith's default direct-subpackage strategy would therefore treat that
 * grouping package as one application module and hide its feature contracts from
 * the IDE. This strategy makes {@code @ApplicationModule} package declarations
 * the source of truth instead.</p>
 */
public final class ExplicitApplicationModuleDetectionStrategy implements ApplicationModuleDetectionStrategy {

    private final ApplicationModuleDetectionStrategy delegate = ApplicationModuleDetectionStrategy.explictlyAnnotated();

    @Override
    public Stream<JavaPackage> getModuleBasePackages(JavaPackage basePackage) {
        return delegate.getModuleBasePackages(basePackage);
    }
}
