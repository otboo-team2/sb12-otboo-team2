package com.otboo.clothes.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.common.exception.BusinessException;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProductUrlValidatorTest {

    private DnsResolver dnsResolver;
    private ProductUrlValidator validator;

    @BeforeEach
    void setUp() {
        dnsResolver = mock(DnsResolver.class);
        validator = new ProductUrlValidator(dnsResolver);
    }

    @Test
    void acceptsPublicHttpsProductUrl() throws Exception {
        given(dnsResolver.resolve("shop.example.com"))
                .willReturn(List.of(address("93.184.216.34")));

        assertThat(validator.validate("https://shop.example.com/products/1"))
                .isEqualTo(URI.create("https://shop.example.com/products/1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://shop.example.com/products/1",
            "file:///etc/passwd",
            "https://user:password@shop.example.com/products/1",
            "https://shop.example.com:8443/products/1",
            "https://shop.example.com/products/1#detail"
    })
    void rejectsUnsupportedUrlShapes(String rawUrl) {
        assertThatThrownBy(() -> validator.validate(rawUrl))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.INVALID_PRODUCT_URL));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.0.1",
            "169.254.169.254", "100.64.0.1", "::1", "fc00::1", "fe80::1"
    })
    void rejectsNonPublicResolvedAddresses(String host) throws Exception {
        given(dnsResolver.resolve(host)).willReturn(List.of(address(host)));

        assertThatThrownBy(() -> validator.validate(urlForHost(host)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.UNSAFE_PRODUCT_URL));
    }

    @Test
    void rejectsWhenDnsReturnsMixedPublicAndPrivateAddresses() throws Exception {
        given(dnsResolver.resolve("shop.example.com"))
                .willReturn(List.of(address("93.184.216.34"), address("10.0.0.1")));

        assertThatThrownBy(() -> validator.validate("https://shop.example.com/products/1"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.UNSAFE_PRODUCT_URL));
    }

    @Test
    void rejectsBlankOrOverlongUrl() {
        assertThatThrownBy(() -> validator.validate("  "))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.INVALID_PRODUCT_URL));

        assertThatThrownBy(() -> validator.validate("https://shop.example.com/" + "a".repeat(2048)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.INVALID_PRODUCT_URL));
    }

    private static InetAddress address(String host) throws Exception {
        return InetAddress.getByName(host);
    }

    private static String urlForHost(String host) {
        return host.contains(":")
                ? "https://[" + host + "]/products/1"
                : "https://" + host + "/products/1";
    }
}
