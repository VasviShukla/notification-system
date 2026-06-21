package com.notifsys.producer.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // Defaults work for local Docker Kafka / self-hosted Redpanda (no auth).
    // Set these env vars instead if you point at Confluent Cloud or another
    // SASL_SSL-secured provider - see DEPLOYMENT.md.
    @Value("${KAFKA_SECURITY_PROTOCOL:PLAINTEXT}")
    private String securityProtocol;

    @Value("${KAFKA_SASL_MECHANISM:}")
    private String saslMechanism;

    @Value("${KAFKA_SASL_JAAS_CONFIG:}")
    private String saslJaasConfig;

    // PEM-based mutual TLS - used by providers like Aiven's free Kafka tier,
    // which authenticate with a client certificate instead of SASL
    // (security.protocol=SSL). Paste the raw contents of the downloaded
    // ca.pem / service.cert / service.key files into these env vars.
    @Value("${KAFKA_SSL_CA_CERT:}")
    private String sslCaCert;

    @Value("${KAFKA_SSL_CLIENT_CERT:}")
    private String sslClientCert;

    @Value("${KAFKA_SSL_CLIENT_KEY:}")
    private String sslClientKey;

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // Reliability: don't lose or duplicate events on broker hiccups.
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.RETRIES_CONFIG, 5);

        if (!"PLAINTEXT".equalsIgnoreCase(securityProtocol)) {
            props.put("security.protocol", securityProtocol);
            if (!saslMechanism.isBlank()) props.put("sasl.mechanism", saslMechanism);
            if (!saslJaasConfig.isBlank()) props.put("sasl.jaas.config", saslJaasConfig);

            // Inline PEM certs (no keystore/truststore files needed on disk).
            if (!sslCaCert.isBlank()) {
                props.put("ssl.truststore.type", "PEM");
                props.put("ssl.truststore.certificates", sslCaCert);
            }
            if (!sslClientCert.isBlank() && !sslClientKey.isBlank()) {
                props.put("ssl.keystore.type", "PEM");
                props.put("ssl.keystore.certificate.chain", sslClientCert);
                props.put("ssl.keystore.key", sslClientKey);
            }
        }

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
