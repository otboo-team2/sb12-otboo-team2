package com.otboo.notification.broadcast;

public interface EventBroadcaster {
    void broadcast(String channel, Object message);
}
