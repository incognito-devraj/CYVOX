package com.cyvox.util;

import java.net.URL;

public final class ResourceResolver {

    private ResourceResolver() {
    }

    public static URL requireResource(String resourcePath) {
        URL resource = ResourceResolver.class.getClassLoader().getResource(resourcePath);
        if (resource == null) {
            throw new IllegalStateException("Missing resource: " + resourcePath);
        }
        return resource;
    }
}
