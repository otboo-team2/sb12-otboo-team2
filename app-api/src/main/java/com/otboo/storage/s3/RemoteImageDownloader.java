package com.otboo.storage.s3;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** 외부 결과 이미지를 제한된 크기로 내려받는다. 리다이렉트와 내부망 접근은 허용하지 않는다. */
public class RemoteImageDownloader {

    private final HttpClient httpClient;
    private final S3StorageProperties properties;

    public RemoteImageDownloader(HttpClient httpClient, S3StorageProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    public RemoteImage download(URI source) {
        validatePublicHttps(source);
        HttpRequest request = HttpRequest.newBuilder(source)
                .timeout(properties.readTimeout())
                .header("Accept", "image/*")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw invalidImage("remote status=" + response.statusCode());
            }
            long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declaredLength > properties.maxSize()) {
                throw invalidImage("remote image is too large");
            }
            String contentType = response.headers().firstValue("Content-Type").orElse(null);
            try (InputStream body = response.body()) {
                return new RemoteImage(readBounded(body), contentType);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(CommonErrorCode.STORAGE_ERROR, e);
        } catch (IOException e) {
            throw new BusinessException(CommonErrorCode.STORAGE_ERROR, e);
        }
    }

    private byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > properties.maxSize()) {
                throw invalidImage("remote image is too large");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private void validatePublicHttps(URI source) {
        if (source == null || !"https".equalsIgnoreCase(source.getScheme())
                || source.getHost() == null || source.getUserInfo() != null) {
            throw invalidImage("only public HTTPS URLs are allowed");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(source.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    throw invalidImage("internal network addresses are not allowed");
                }
            }
        } catch (IOException e) {
            throw new BusinessException(CommonErrorCode.STORAGE_ERROR, e);
        }
    }

    private static BusinessException invalidImage(String reason) {
        return new BusinessException(CommonErrorCode.INVALID_IMAGE).addDetail("reason", reason);
    }

    public record RemoteImage(byte[] body, String contentType) {
    }
}
