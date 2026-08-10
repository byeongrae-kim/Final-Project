package com.ex.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "feedflow.payment")
public class PaymentProperties {

    private Portone portone = new Portone();

    @Getter
    @Setter
    public static class Portone {
        /** V1 SDK의 IMP.init()에 전달하는 impXXXXXXXX 형식의 고객사 식별코드입니다. */
        private String customerCode = "";

        /** V1 REST API 액세스 토큰을 발급할 때만 서버에서 사용하는 값입니다. */
        private String apiKey = "";
        private String apiSecret = "";

        /** 포트원 콘솔에서 테스트/실연동 PG 채널을 등록한 뒤 발급되는 채널 키입니다. */
        private String cardChannelKey = "";
        private String kakaoChannelKey = "";
        private String virtualAccountChannelKey = "";
    }
}
