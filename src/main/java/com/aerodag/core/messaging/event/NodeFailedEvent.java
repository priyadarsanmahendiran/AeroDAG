package com.aerodag.core.messaging.event;

import java.util.UUID;

public record NodeFailedEvent(UUID planId, UUID nodeId, String errorMessage) {}
