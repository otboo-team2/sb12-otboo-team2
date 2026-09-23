package com.otboo.dm.util;

import java.util.UUID;

public final class DmKeyGenerator {

    private DmKeyGenerator() {}

    public static String generate(UUID userId1, UUID userId2) {
        String id1 = userId1.toString();
        String id2 = userId2.toString();
        return id1.compareTo(id2) < 0 ? id1 + "_" + id2 : id2 + "_" + id1;
    }
}
