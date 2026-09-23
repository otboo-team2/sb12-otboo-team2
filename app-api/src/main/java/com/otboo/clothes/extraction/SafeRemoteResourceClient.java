package com.otboo.clothes.extraction;

import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.http.ExternalApiClient;
import com.otboo.common.http.ExternalApiClientFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 자동 리다이렉트를 끄고 URL과 응답 크기를 직접 검증하는 외부 리소스 클라이언트다. */
@Component
public class SafeRemoteResourceClient {

    private static final String USER_AGENT = "OtbooProductExtractor/1.0";
    private static final String ACCEPT = "text/html,application/xhtml+xml,image/*";
    private static final String PRODUCT_PAGE_ENDPOINT = "GET product-page";
    private static final String JPEG = "image/jpeg";
    private static final String PNG = "image/png";
    private static final String WEBP = "image/webp";

    private final ExternalApiClient api;
    private final ProductUrlValidator urlValidator;
    private final ClothesExtractionProperties properties;

    public SafeRemoteResourceClient(
            ExternalApiClientFactory factory,
            ProductUrlValidator urlValidator,
            ClothesExtractionProperties properties
    ) {
        this.api = factory.create(
                "link-extract",
                HttpClient.Redirect.NEVER,
                builder -> builder
                        .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                        .defaultHeader(HttpHeaders.ACCEPT, ACCEPT));
        this.urlValidator = urlValidator;
        this.properties = properties;
    }

    public RemoteResource getHtml(URI uri) {
        return download(uri, properties.maxHtmlBytes(), false);
    }

    public RemoteResource getImage(URI uri) {
        return download(uri, properties.maxImageBytes(), true);
    }

    private RemoteResource download(URI uri, int maxBytes, boolean image) {
        URI current = urlValidator.validate(uri);
        int redirects = 0;

        while (true) {
            RawResponse response = request(current, maxBytes, image);
            if (isRedirect(response.status())) {
                if (redirects >= properties.maxRedirects() || response.location() == null) {
                    throw new BusinessException(ClothesErrorCode.UNSAFE_PRODUCT_URL);
                }

                URI redirectUri;
                try {
                    redirectUri = current.resolve(response.location());
                } catch (IllegalArgumentException exception) {
                    throw new BusinessException(ClothesErrorCode.INVALID_PRODUCT_URL, exception);
                }
                current = urlValidator.validate(redirectUri);
                redirects++;
                continue;
            }

            if (response.status() < 200 || response.status() >= 300) {
                throw new BusinessException(ClothesErrorCode.PRODUCT_DATA_NOT_FOUND);
            }

            String contentType = image
                    ? detectImageContentType(response.body())
                    : validateHtmlContentType(response.contentType());
            return new RemoteResource(current, contentType, response.body());
        }
    }

    private RawResponse request(URI uri, int maxBytes, boolean image) {
        return api.exchange(PRODUCT_PAGE_ENDPOINT, restClient -> restClient.get()
                .uri(uri)
                .exchange((request, response) -> readResponse(response, maxBytes, image)));
    }

    private RawResponse readResponse(ClientHttpResponse response, int maxBytes, boolean image) {
        HttpStatusCode status;
        try {
            status = response.getStatusCode();
        } catch (IOException exception) {
            throw new BusinessException(
                    image ? ClothesErrorCode.INVALID_REMOTE_IMAGE : ClothesErrorCode.PRODUCT_DATA_NOT_FOUND,
                    exception);
        }
        String contentType = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);

        if (isRedirect(status.value())) {
            return new RawResponse(status.value(), contentType, location, new byte[0]);
        }

        ErrorCodeForResource errorCode = image
                ? ErrorCodeForResource.IMAGE
                : ErrorCodeForResource.HTML;
        try {
            return new RawResponse(
                    status.value(),
                    contentType,
                    location,
                    readBounded(response.getBody(), maxBytes, errorCode));
        } catch (IOException exception) {
            throw new BusinessException(
                    image ? ClothesErrorCode.INVALID_REMOTE_IMAGE : ClothesErrorCode.PRODUCT_DATA_NOT_FOUND,
                    exception);
        }
    }

    private static byte[] readBounded(
            InputStream input,
            int maxBytes,
            ErrorCodeForResource errorCode
    ) throws IOException {
        if (maxBytes < 0) {
            throw tooLarge(errorCode);
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        long total = 0;
        long limitPlusOne = (long) maxBytes + 1;

        while (total < limitPlusOne) {
            int requested = (int) Math.min(buffer.length, limitPlusOne - total);
            int read = input.read(buffer, 0, requested);
            if (read == -1) {
                break;
            }
            total += read;
            if (total > maxBytes) {
                throw tooLarge(errorCode);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String validateHtmlContentType(String contentType) {
        if (contentType != null
                && !contentType.toLowerCase().startsWith("text/html")
                && !contentType.toLowerCase().startsWith("application/xhtml+xml")) {
            throw new BusinessException(ClothesErrorCode.PRODUCT_DATA_NOT_FOUND);
        }
        return contentType;
    }

    private static String detectImageContentType(byte[] bytes) {
        if (hasJpegMagic(bytes)) {
            return JPEG;
        }
        if (hasPngMagic(bytes)) {
            return PNG;
        }
        if (hasWebpMagic(bytes)) {
            return WEBP;
        }
        throw new BusinessException(ClothesErrorCode.INVALID_REMOTE_IMAGE);
    }

    private static boolean hasJpegMagic(byte[] bytes) {
        return bytes != null
                && bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff;
    }

    private static boolean hasPngMagic(byte[] bytes) {
        byte[] signature = {
                (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};
        if (bytes == null || bytes.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (bytes[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasWebpMagic(byte[] bytes) {
        return bytes != null
                && bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P';
    }

    private static BusinessException tooLarge(ErrorCodeForResource errorCode) {
        return new BusinessException(errorCode == ErrorCodeForResource.IMAGE
                ? ClothesErrorCode.INVALID_REMOTE_IMAGE
                : ClothesErrorCode.REMOTE_RESOURCE_TOO_LARGE);
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private enum ErrorCodeForResource {
        HTML,
        IMAGE
    }

    private record RawResponse(int status, String contentType, String location, byte[] body) {
    }
}
