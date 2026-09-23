package com.otboo.clothes.extraction;

import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.common.exception.BusinessException;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Component;

/** 사용자가 제공한 상품 URL과 리다이렉트 목적지의 SSRF 위험을 검사한다. */
@Component
public class ProductUrlValidator {

    private static final int MAX_URL_LENGTH = 2048;

    private final DnsResolver dnsResolver;

    public ProductUrlValidator(DnsResolver dnsResolver) {
        this.dnsResolver = dnsResolver;
    }

    public URI validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank() || rawUrl.length() > MAX_URL_LENGTH) {
            throw invalidUrl();
        }

        try {
            return validate(URI.create(rawUrl.trim()));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ClothesErrorCode.INVALID_PRODUCT_URL, exception);
        }
    }

    public URI validate(URI uri) {
        if (uri == null
                || !"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getHost().isBlank()
                || uri.getUserInfo() != null
                || uri.getFragment() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw invalidUrl();
        }

        String host = removeIpv6Brackets(uri.getHost());
        List<InetAddress> addresses = dnsResolver.resolve(host);
        if (addresses.isEmpty() || addresses.stream().anyMatch(ProductUrlValidator::isForbiddenAddress)) {
            throw new BusinessException(ClothesErrorCode.UNSAFE_PRODUCT_URL);
        }
        return uri;
    }

    private static BusinessException invalidUrl() {
        return new BusinessException(ClothesErrorCode.INVALID_PRODUCT_URL);
    }

    private static String removeIpv6Brackets(String host) {
        if (host.length() >= 2 && host.charAt(0) == '[' && host.charAt(host.length() - 1) == ']') {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    private static boolean isForbiddenAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        return isCarrierGradeNat(bytes) || isIpv6UniqueLocal(bytes);
    }

    private static boolean isCarrierGradeNat(byte[] address) {
        return address.length == 4
                && (address[0] & 0xff) == 100
                && (address[1] & 0xc0) == 64;
    }

    private static boolean isIpv6UniqueLocal(byte[] address) {
        return address.length == 16 && (address[0] & 0xfe) == 0xfc;
    }
}
