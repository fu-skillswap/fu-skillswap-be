package com.fptu.exe.skillswap.modules.blog.port;

import java.util.List;

/** Public, reader-safe metadata required to render a blog social preview. */
public interface BlogShareQueryPort {

    BlogShareMetadata findPublishedShareMetadata(String slug);

    /** Slugs that are safe to publish in the public sitemap. */
    List<String> findPublicPublishedSlugs();
}
