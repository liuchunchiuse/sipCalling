package com.net.sipcall.sipcalling.config;

import com.net.sipcall.sipcalling.service.demo.SipMessageProcessor;
import com.net.sipcall.sipcalling.service.demo.SipPhone;
import com.net.sipcall.sipcalling.service.demo.SipServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class SipConfig {

    @Bean
    public SipServer sipServer(SipMessageProcessor messageProcessor) {
        try {
            SipServer sipServerLayer = new SipServer();
            sipServerLayer.setMessageProcessor(messageProcessor);
            log.info("SIP服务启动完毕");
            return sipServerLayer;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


   /* @Bean
    public SipPhone sipPhone() {
        try {
            SipPhone sipServerLayer = new SipPhone();
            sipServerLayer.init();
            log.info("SIP服务启动完毕");
            return sipServerLayer;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }*/

}
