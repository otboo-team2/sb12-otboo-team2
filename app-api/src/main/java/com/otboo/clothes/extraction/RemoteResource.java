package com.otboo.clothes.extraction;

import java.net.URI;

/** 크기 제한과 URL 검증을 통과한 외부 리소스다. */
public record RemoteResource(URI finalUri, String contentType, byte[] body) {

    public RemoteResource {
        body = body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
