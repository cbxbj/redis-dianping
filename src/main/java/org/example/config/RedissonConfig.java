package org.example.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        // 可设置 哨兵 集群等服务器
        SingleServerConfig serverConfig = config.useSingleServer();
        serverConfig.setAddress("redis://aliyun:6379")
                .setPassword("123456");
        return Redisson.create(config);
    }
}
