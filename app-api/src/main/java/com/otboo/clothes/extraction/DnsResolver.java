package com.otboo.clothes.extraction;

import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.common.exception.BusinessException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

/** 사용자 URL의 호스트가 실제로 어떤 IP로 해석되는지 확인하는 경계다. */
@Component
public class DnsResolver {

    public List<InetAddress> resolve(String host) {
        try {
            return Arrays.asList(InetAddress.getAllByName(host));
        } catch (UnknownHostException exception) {
            throw new BusinessException(ClothesErrorCode.UNSAFE_PRODUCT_URL, exception);
        }
    }
}
