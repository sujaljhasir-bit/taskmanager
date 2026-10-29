package com.metropolitan.service;

import java.util.Set;

/** Domain event published when a task changes. {@code audience} = users allowed to know about it. */
public record TaskChange(String action, Long taskId, Set<Long> audience) {}
