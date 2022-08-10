package com.learnkafka.integration.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnkafka.consumer.LibraryEventsConsumer;
import com.learnkafka.entity.Book;
import com.learnkafka.entity.LibraryEvent;
import com.learnkafka.entity.LibraryEventType;
import com.learnkafka.jpa.LibraryEventsRepository;
import com.learnkafka.service.LibraryEventsService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@EmbeddedKafka(partitions = 3, topics = {"library-events"}, brokerProperties = {
        "listeners=PLAINTEXT://localhost:9092",
        "listeners=PLAINTEXT://localhost:9093",
        "listeners=PLAINTEXT://localhost:9094"})
class LibraryEventConsumerIntegrationTest {

    @Autowired
    EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    KafkaTemplate<UUID, String> kafkaTemplate;

    @Autowired
    KafkaListenerEndpointRegistry endpointRegistry;

    @Autowired
    LibraryEventsRepository repository;

    @Autowired
    ObjectMapper objectMapper;

    @SpyBean
    LibraryEventsConsumer libraryEventsConsumerSpy;

    @SpyBean
    LibraryEventsService libraryEventsServiceSpy;

    @BeforeEach
    void setUp() {
        for (MessageListenerContainer messageListenerContainer : endpointRegistry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(messageListenerContainer, embeddedKafkaBroker.getPartitionsPerTopic());
        }
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void publishNewLibraryEvent() throws ExecutionException, InterruptedException, JsonProcessingException {
        String expectedRecord = "{\"libraryEventId\":null," +
                "\"libraryEventType\":\"NEW\"," +
                "\"book\":{" +
                "\"bookId\":456," +
                "\"bookName\":\"Kafka using Spring boot\"," +
                "\"bookAuthor\":\"Dilip\"}" +
                "}";

        CountDownLatch latch = new CountDownLatch(1);
        latch.await(3, TimeUnit.SECONDS);

        ConsumerRecord<UUID, String> message = new ConsumerRecord<UUID, String>("library-events", 1, 1, 1l, TimestampType.CREATE_TIME, 1l,
                10, 10, UUID.randomUUID(), expectedRecord);

        libraryEventsConsumerSpy.onMessage(message);

        verify(libraryEventsConsumerSpy, times(1)).onMessage(isA(ConsumerRecord.class));
        verify(libraryEventsServiceSpy, times(1)).processLibraryEvent(isA(ConsumerRecord.class));

        List<LibraryEvent> libraryEventList = (List<LibraryEvent>) repository.findAll();
        assert libraryEventList.size() == 1;
        libraryEventList.forEach(libraryEvent -> {
            assert libraryEvent.getLibraryEventId() != null;
            assertEquals(456, libraryEvent.getBook().getBookId());
        });
    }

    @Test
    void publishUpdateLibraryEvent() throws InterruptedException, JsonProcessingException {
        String expectedRecord = "{\"libraryEventId\":\"b83e423c-957b-43ca-be00-90e67c6dbdef\"," +
                "\"libraryEventType\":\"NEW\"," +
                "\"book\":{" +
                "\"bookId\":456," +
                "\"bookName\":\"Kafka using Spring boot\"," +
                "\"bookAuthor\":\"Dilip\"}" +
                "}";
        LibraryEvent libraryEvent = objectMapper.readValue(expectedRecord, LibraryEvent.class);
        libraryEvent.getBook().setLibraryEvent(libraryEvent);
        repository.save(libraryEvent);
        libraryEvent.setLibraryEventType(LibraryEventType.UPDATE);

        Book updatedBook = Book.builder()
                .bookId(456)
                .bookName("Kafka using Spring Boot 2.x")
                .bookAuthor("Dilip")
                .build();

        libraryEvent.setBook(updatedBook);
        String updatedMessage = objectMapper.writeValueAsString(libraryEvent);

        ConsumerRecord<UUID, String> message = new ConsumerRecord<UUID, String>("library-events", 1, 1, 1l, TimestampType.CREATE_TIME, 1l,
                10, 10, UUID.fromString("b83e423c-957b-43ca-be00-90e67c6dbdef"), expectedRecord);
        libraryEventsConsumerSpy.onMessage(message);

        CountDownLatch latch = new CountDownLatch(1);
        latch.await(3, TimeUnit.SECONDS);

        verify(libraryEventsConsumerSpy, times(1)).onMessage(isA(ConsumerRecord.class));
        verify(libraryEventsServiceSpy, times(1)).processLibraryEvent(isA(ConsumerRecord.class));
        Optional<LibraryEvent> persistedLibraryEvent = repository.findById(libraryEvent.getLibraryEventId());
        persistedLibraryEvent.ifPresent(event -> assertEquals("Kafka using Spring Boot 2.x", event.getBook().getBookName()));
    }

    @Test
    void publishUpdateLibraryEvent_null_LibraryEvent() throws ExecutionException, InterruptedException, JsonProcessingException {
        String json = "{\"libraryEventId\": null," +
                "\"libraryEventType\":\"UPDATE\"," +
                "\"book\":{" +
                "\"bookId\":456," +
                "\"bookName\":\"Kafka using Spring boot\"," +
                "\"bookAuthor\":\"Dilip\"}" +
                "}";

        ConsumerRecord<UUID, String> message = new ConsumerRecord<UUID, String>("library-events", 1, 1, 1L, TimestampType.CREATE_TIME, 1L,
                10, 10, null, json);



        CountDownLatch latch = new CountDownLatch(1);
        latch.await(5, TimeUnit.SECONDS);

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            libraryEventsConsumerSpy.onMessage(message);
            verify(libraryEventsConsumerSpy, times(1)).onMessage(isA(ConsumerRecord.class));
            verify(libraryEventsServiceSpy, times(2)).processLibraryEvent(isA(ConsumerRecord.class));
        });
    }

    @Test
    void publishUpdateLibraryEvent_not_find_id_LibraryEvent() throws ExecutionException, InterruptedException, JsonProcessingException {
        String json = "{\"libraryEventId\": \"b83e423c-957b-43ca-be00-111111111111\"," +
                "\"libraryEventType\":\"UPDATE\"," +
                "\"book\":{" +
                "\"bookId\":456," +
                "\"bookName\":\"Kafka using Spring boot\"," +
                "\"bookAuthor\":\"Dilip\"}" +
                "}";

        ConsumerRecord<UUID, String> message = new ConsumerRecord<UUID, String>("library-events", 1, 1, 1L, TimestampType.CREATE_TIME, 1L,
                10, 10, UUID.fromString("b83e423c-957b-43ca-be00-111111111111"), json);



        CountDownLatch latch = new CountDownLatch(1);
        latch.await(5, TimeUnit.SECONDS);

        IllegalArgumentException illegalArgumentException = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            libraryEventsConsumerSpy.onMessage(message);
            verify(libraryEventsConsumerSpy, times(1)).onMessage(isA(ConsumerRecord.class));
            verify(libraryEventsServiceSpy, times(2)).processLibraryEvent(isA(ConsumerRecord.class));
        });
        assertEquals("Po meu nobre, tem nada aqui com esse ID não o", illegalArgumentException.getMessage());
    }
}