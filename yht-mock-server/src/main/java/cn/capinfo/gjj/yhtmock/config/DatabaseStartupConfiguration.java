package cn.capinfo.gjj.yhtmock.config;

import cn.capinfo.gjj.yhtmock.service.MockStoreService;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseStartupConfiguration {

    @Bean
    SmartInitializingSingleton initializeStoredSettings(MockStoreService store) {
        return store::applyStartupSettings;
    }
}
