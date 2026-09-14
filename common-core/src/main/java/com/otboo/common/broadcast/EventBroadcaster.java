package com.otboo.common.broadcast;

public interface EventBroadcaster {
    void broadcast(String channel, Object message);
}
