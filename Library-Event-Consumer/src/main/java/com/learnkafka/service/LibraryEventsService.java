package com.learnkafka.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnkafka.entity.LibraryEvent;
import com.learnkafka.jpa.LibraryEventsRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
public class LibraryEventsService {

    private final ObjectMapper objectMapper;

    private final LibraryEventsRepository repository;

    public LibraryEventsService(ObjectMapper objectMapper, LibraryEventsRepository repository) {
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    public void processLibraryEvent(ConsumerRecord<UUID, String> consumerRecord) throws JsonProcessingException {
        LibraryEvent libraryEvent = objectMapper.readValue(consumerRecord.value(), LibraryEvent.class);
        log.info("LibraryEvent : {}", libraryEvent);


        switch (libraryEvent.getLibraryEventType()) {
            case NEW -> save(libraryEvent);
            case UPDATE -> {
                validate(libraryEvent);
                save(libraryEvent);
            }
            default -> log.error("Invalid Library Event Type");
        }
    }

    private void validate(LibraryEvent libraryEvent) {
        if (Objects.isNull(libraryEvent.getLibraryEventId()))
            throw new IllegalArgumentException("Mano o Id ta vazio po, ta faltando aqui consagrado ");

        repository.findById(libraryEvent.getLibraryEventId()).orElseThrow(() -> new IllegalArgumentException("Po meu nobre, tem nada aqui com esse ID não o"));
        log.info("Validação foi um sucesso");
    }

    private void save(LibraryEvent libraryEvent) {
        libraryEvent.getBook().setLibraryEvent(libraryEvent);
        repository.save(libraryEvent);
        log.info("Successfully the library Event : {}", libraryEvent);
    }

}