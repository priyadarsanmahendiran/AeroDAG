package com.aerodag.core.messaging.event;

import java.util.UUID;

public record NodeCompletedEvent(UUID planId, UUID nodeId) {}
