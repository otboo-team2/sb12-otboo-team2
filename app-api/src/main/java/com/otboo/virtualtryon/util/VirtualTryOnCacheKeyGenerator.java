package com.otboo.virtualtryon.util;

import java.util.UUID;

public final class VirtualTryOnCacheKeyGenerator {
    private VirtualTryOnCacheKeyGenerator() {}

    public static String generate(String modelHash, UUID topId, UUID bottomId, UUID additionalId) {
        String additional = additionalId != null ? additionalId.toString() : "none";
        return modelHash + "_" + topId + "_" + bottomId + "_" + additional;
    }
}
