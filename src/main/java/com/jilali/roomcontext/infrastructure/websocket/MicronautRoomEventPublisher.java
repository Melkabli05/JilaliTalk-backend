package com.jilali.roomcontext.infrastructure.websocket;

import com.jilali.roomcontext.application.port.out.RoomEventPublisherPort;
import com.jilali.roomcontext.domain.event.RoomEvent;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Singleton;

@Singleton
public class MicronautRoomEventPublisher implements RoomEventPublisherPort {

    private final ApplicationEventPublisher<RoomEvent> publisher;

    public MicronautRoomEventPublisher(ApplicationEventPublisher<RoomEvent> publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(RoomEvent event) {
        publisher.publishEvent(event);
    }
}
