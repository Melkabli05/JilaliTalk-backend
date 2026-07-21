package com.jilali.roomcontext.application.port.out;

import com.jilali.roomcontext.domain.event.RoomEvent;

public interface RoomEventPublisherPort {
    void publish(RoomEvent event);
}
