package com.ecommerce.kafkaadmin.service;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.DescribeConfigsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.ConsumerGroupState;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Service
public class KafkaAdminService {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private AdminClient adminClient() {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("client.id", "tuki-kafka-admin");
        props.put("request.timeout.ms", "10000");
        return AdminClient.create(props);
    }

    public List<Map<String, Object>> listTopics() {
        try (AdminClient client = adminClient()) {
            Set<String> names = client.listTopics().names().get();
            Map<String, TopicDescription> descriptions = client.describeTopics(names).all().get();
            List<Map<String, Object>> result = new ArrayList<>();
            for (String name : names) {
                TopicDescription d = descriptions.get(name);
                Map<String, Object> item = new HashMap<>();
                item.put("name", name);
                item.put("partitions", d == null ? -1 : d.partitions().size());
                item.put("replicationFactor", d == null || d.partitions().isEmpty()
                        ? -1 : d.partitions().get(0).replicas().size());
                item.put("internal", d == null ? false : d.isInternal());
                result.add(item);
            }
            result.sort((a, b) -> String.valueOf(a.get("name")).compareTo(String.valueOf(b.get("name"))));
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido al listar tópicos", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("No se pudieron listar los tópicos: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> createTopic(String name, int partitions, short replicationFactor,
                                           Long retentionMs) {
        try (AdminClient client = adminClient()) {
            NewTopic topic = new NewTopic(name, partitions, replicationFactor);
            if (retentionMs != null) {
                topic.configs(Map.of("retention.ms", String.valueOf(retentionMs)));
            }
            client.createTopics(List.of(topic)).all().get();
            return Map.of("created", name, "partitions", partitions,
                    "replicationFactor", replicationFactor);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido al crear el tópico", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("No se pudo crear el tópico: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> updateConfig(String name, String key, String value) {
        try (AdminClient client = adminClient()) {
            ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, name);
            Config config = new Config(List.of(new ConfigEntry(key, value)));
            client.alterConfigs(Map.of(resource, config)).all().get();
            return Map.of("topic", name, "key", key, "value", value);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido al modificar la configuración", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("No se pudo modificar la configuración: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> getConfig(String name) {
        try (AdminClient client = adminClient()) {
            ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, name);
            DescribeConfigsResult result = client.describeConfigs(List.of(resource));
            Config config = result.all().get().get(resource);
            Map<String, String> entries = new HashMap<>();
            if (config != null) {
                for (ConfigEntry entry : config.entries()) {
                    if (entry.value() != null) {
                        entries.put(entry.name(), entry.value());
                    }
                }
            }
            return Map.of("topic", name, "configs", entries);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido al leer la configuración", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("No se pudo leer la configuración: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> deleteTopic(String name) {
        try (AdminClient client = adminClient()) {
            client.deleteTopics(List.of(name)).all().get();
            return Map.of("deleted", name);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido al borrar el tópico", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("No se pudo borrar el tópico: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> listGroups() {
        try (AdminClient client = adminClient()) {
            Set<String> groupIds = client.listConsumerGroups().all().get().stream()
                    .map(g -> g.groupId()).collect(java.util.stream.Collectors.toSet());
            Map<String, ConsumerGroupDescription> descriptions =
                    client.describeConsumerGroups(groupIds).all().get();

            List<Map<String, Object>> result = new ArrayList<>();
            for (String groupId : groupIds) {
                ConsumerGroupDescription d = descriptions.get(groupId);
                Map<String, Object> item = new HashMap<>();
                item.put("groupId", groupId);
                item.put("state", d == null ? "UNKNOWN" : String.valueOf(d.state()));
                item.put("members", d == null ? 0 : d.members().size());
                item.put("assignedTopics", d == null ? List.of() : d.members().stream()
                        .flatMap(m -> m.assignment().topicPartitions().stream())
                        .map(TopicPartition::topic)
                        .distinct()
                        .toList());
                ConsumerGroupState state = d == null ? null : d.state();
                item.put("stable", state == ConsumerGroupState.STABLE);
                List<Map<String, Object>> offsets = offsetsForGroup(client, groupId);
                item.put("offsets", offsets);
                item.put("totalLag", offsets.stream()
                        .mapToLong(o -> (Long) o.get("lag"))
                        .sum());
                result.add(item);
            }
            result.sort((a, b) -> String.valueOf(a.get("groupId")).compareTo(String.valueOf(b.get("groupId"))));
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido al listar consumer groups", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("No se pudieron listar consumer groups: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> getGroupOffsets(String groupId) {
        try (AdminClient client = adminClient()) {
            List<Map<String, Object>> offsets = offsetsForGroup(client, groupId);
            return Map.of(
                    "groupId", groupId,
                    "offsets", offsets,
                    "totalLag", offsets.stream().mapToLong(o -> (Long) o.get("lag")).sum());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido al consultar offsets", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("No se pudieron consultar los offsets: " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> offsetsForGroup(AdminClient client, String groupId)
            throws InterruptedException, ExecutionException {
        Map<TopicPartition, OffsetAndMetadata> committed = client
                .listConsumerGroupOffsets(groupId)
                .partitionsToOffsetAndMetadata()
                .get();
        Map<TopicPartition, Long> endOffsets = committed.isEmpty()
                ? Map.of() : readEndOffsets(committed.keySet());

        List<Map<String, Object>> offsets = new ArrayList<>();
        for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : committed.entrySet()) {
            TopicPartition partition = entry.getKey();
            long current = entry.getValue() == null ? -1 : entry.getValue().offset();
            Long end = endOffsets.get(partition);
            long endOffset = end == null ? -1 : end;
            long lag = current >= 0 && endOffset >= current ? endOffset - current : 0;
            Map<String, Object> item = new HashMap<>();
            item.put("topic", partition.topic());
            item.put("partition", partition.partition());
            item.put("currentOffset", current);
            item.put("endOffset", endOffset);
            item.put("lag", lag);
            offsets.add(item);
        }
        offsets.sort((a, b) -> {
            int byTopic = String.valueOf(a.get("topic")).compareTo(String.valueOf(b.get("topic")));
            return byTopic != 0 ? byTopic
                    : Integer.compare((Integer) a.get("partition"), (Integer) b.get("partition"));
        });
        return offsets;
    }

    private Map<TopicPartition, Long> readEndOffsets(Set<TopicPartition> partitions) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "tuki-kafka-lag-" + UUID.randomUUID());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            return consumer.endOffsets(partitions);
        }
    }
}
