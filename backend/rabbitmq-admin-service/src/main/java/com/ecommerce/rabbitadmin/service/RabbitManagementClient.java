package com.ecommerce.rabbitadmin.service;

import com.ecommerce.rabbitadmin.config.RabbitAdminProperties;
import com.ecommerce.rabbitadmin.dto.BindingInfo;
import com.ecommerce.rabbitadmin.dto.ExchangeInfo;
import com.ecommerce.rabbitadmin.dto.QueueInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Cliente de solo lectura contra la Management HTTP API de RabbitMQ. Se usa
 * para listar colas/exchanges/bindings, ya que AMQP no ofrece un listado.
 *
 * Si la API no esta disponible o esta deshabilitada, se degrada de forma
 * controlada devolviendo listas vacias (nunca rompe la operacion principal).
 */
@Component
public class RabbitManagementClient {

    private static final Logger log = LoggerFactory.getLogger(RabbitManagementClient.class);

    private final RabbitAdminProperties props;
    private final RestClient restClient;
    private final String virtualHost;

    public RabbitManagementClient(RabbitAdminProperties props,
                                  @Value("${spring.rabbitmq.virtual-host:/}") String virtualHost) {
        this.props = props;
        this.virtualHost = virtualHost;
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeaders(headers ->
                        headers.setBasicAuth(props.getUsername(), props.getPassword()))
                .build();
    }

    public List<QueueInfo> listQueues() {
        return getList("/api/queues/{vhost}", new ParameterizedTypeReference<>() {
        });
    }

    public List<ExchangeInfo> listExchanges() {
        return getList("/api/exchanges/{vhost}", new ParameterizedTypeReference<>() {
        });
    }

    public List<BindingInfo> listBindings() {
        return getList("/api/bindings/{vhost}", new ParameterizedTypeReference<>() {
        });
    }

    private <T> List<T> getList(String uri, ParameterizedTypeReference<List<T>> type) {
        if (!props.isEnabled()) {
            log.debug("Management API deshabilitada; se devuelve lista vacia");
            return List.of();
        }
        try {
            List<T> body = restClient.get()
                    .uri(uri, virtualHost)
                    .retrieve()
                    .body(type);
            return body == null ? List.of() : body;
        } catch (Exception ex) {
            log.warn("No se pudo consultar la Management API ({}): {}", uri, ex.getMessage());
            return List.of();
        }
    }
}
