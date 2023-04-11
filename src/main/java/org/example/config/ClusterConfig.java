package org.example.config;

import io.lettuce.core.ReadFrom;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;

//@Component
public class ClusterConfig {

    /**
     * 配置redis的读写策略
     * 1、{@link ReadFrom#MASTER}            从主节点读取
     * 2、{@link ReadFrom#MASTER_PREFERRED}  优先从master节点读取，master不可用才读取replica
     * 3、{@link ReadFrom#REPLICA}           从slave（replica）节点读取
     * 4、{@link ReadFrom#REPLICA_PREFERRED} 优先从slave（replica）节点读取，所有的slave都不可用才读取master
     */
    @Bean
    public LettuceClientConfigurationBuilderCustomizer clientConfigurationBuilderCustomizer() {
        return clientConfigurationBuilder -> clientConfigurationBuilder
                .readFrom(ReadFrom.REPLICA_PREFERRED);
    }
}
