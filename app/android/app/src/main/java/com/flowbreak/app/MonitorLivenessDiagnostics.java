package com.flowbreak.app;

/**
 * In-memory diagnostics for attributing long monitor-loop gaps.
 *
 * <p>This class records observations only. It does not schedule, restart, or
 * otherwise alter the monitor loop.</p>
 */
final class MonitorLivenessDiagnostics {
    static final String ACTION_START = "START";
    static final String ACTION_RELOAD = "RELOAD";
    static final String ACTION_STOP = "STOP";
    static final String ACTION_BEGIN_REST = "BEGIN_REST";
    static final String ACTION_COMPLETE_REST = "COMPLETE_REST";
    static final String ACTION_CANCEL_REST = "CANCEL_REST";
    static final String ACTION_EMERGENCY = "EMERGENCY";
    static final String ACTION_UNKNOWN = "UNKNOWN";

    static final String LOOP_REASON_ENGINE_COMMAND_START = "ENGINE_COMMAND_START";
    static final String LOOP_REASON_USER_STOP = "USER_STOP";
    static final String LOOP_REASON_SERVICE_DESTROY = "SERVICE_DESTROY";
    static final String LOOP_REASON_UNSUPPORTED_RUNTIME = "UNSUPPORTED_RUNTIME";
    static final String LOOP_REASON_OTHER = "OTHER";

    private int processPid;
    private long processInstanceStartedElapsedMs;
    private long processInstanceStartedWallMs;
    private boolean processIdentityInitialized;

    private String serviceInstanceId = "";
    private long currentServiceGeneration;
    private long serviceCreateCount;
    private long serviceStartCommandCount;
    private long serviceDestroyCount;
    private long serviceTaskRemovedCount;
    private long serviceInstanceStartedElapsedMs;
    private long serviceInstanceStartedWallMs;
    private long lastStartCommandElapsedMs;
    private long lastStartCommandWallMs;
    private String lastStartCommandAction = ACTION_UNKNOWN;
    private String lastStartCommandServiceInstanceId = "";
    private long lastStartCommandServiceGeneration;
    private long lastServiceDestroyElapsedMs;
    private long lastServiceDestroyWallMs;
    private String lastDestroyedServiceInstanceId = "";
    private long lastDestroyedServiceGeneration;
    private long lastTaskRemovedElapsedMs;
    private long lastTaskRemovedWallMs;
    private String lastTaskRemovedServiceInstanceId = "";
    private long lastTaskRemovedServiceGeneration;

    private boolean monitorLoopActive;
    private boolean monitorLoopShutdown;
    private long monitorLoopStartCount;
    private long monitorLoopStopCount;
    private long monitorLoopShutdownCount;
    private String lastMonitorLoopStartReason = LOOP_REASON_OTHER;
    private String lastMonitorLoopStopReason = LOOP_REASON_OTHER;
    private String lastMonitorLoopShutdownReason = LOOP_REASON_OTHER;

    private long lastMonitorStartElapsedMs;
    private long lastMonitorStartWallMs;
    private long lastMonitorEndElapsedMs;
    private long lastMonitorEndWallMs;
    private long lastCallbackScheduledElapsedMs;
    private long scheduledCallbackDueElapsedMs;
    private long pendingCallbackDueElapsedMs;
    private boolean callbackDeadlinePending;
    private long actualMonitorStartElapsedMs;
    private long callbackLatenessMs;
    private long maxCallbackLatenessMs;
    private long callbackLatenessOver3000Count;
    private long callbackLatenessOver5000Count;

    MonitorLivenessDiagnostics(int processPid, long processInstanceStartedElapsedMs,
            long processInstanceStartedWallMs) {
        if (processPid > 0 || processInstanceStartedElapsedMs > 0L
                || processInstanceStartedWallMs > 0L) {
            initializeProcessIdentity(processPid, processInstanceStartedElapsedMs,
                    processInstanceStartedWallMs);
        }
    }

    synchronized void initializeProcessIdentity(int pid, long startedElapsedMs, long startedWallMs) {
        if (processIdentityInitialized || (pid <= 0 && startedElapsedMs <= 0L
                && startedWallMs <= 0L)) {
            return;
        }
        processPid = pid;
        processInstanceStartedElapsedMs = startedElapsedMs;
        processInstanceStartedWallMs = startedWallMs;
        processIdentityInitialized = true;
    }

    synchronized long recordServiceCreate(long wallMs, long elapsedMs) {
        serviceCreateCount++;
        currentServiceGeneration = serviceCreateCount;
        serviceInstanceId = serviceInstanceIdFor(currentServiceGeneration);
        serviceInstanceStartedWallMs = wallMs;
        serviceInstanceStartedElapsedMs = elapsedMs;
        resetCurrentServiceState();
        return currentServiceGeneration;
    }

    synchronized long currentServiceGeneration() {
        return currentServiceGeneration;
    }

    synchronized boolean isCurrentServiceGeneration(long serviceGeneration) {
        return currentServiceGeneration == serviceGeneration;
    }

    synchronized void recordServiceStartCommand(long serviceGeneration, String action,
            long wallMs, long elapsedMs) {
        serviceStartCommandCount++;
        lastStartCommandAction = normalizeAction(action);
        lastStartCommandWallMs = wallMs;
        lastStartCommandElapsedMs = elapsedMs;
        lastStartCommandServiceGeneration = serviceGeneration;
        lastStartCommandServiceInstanceId = serviceInstanceIdFor(serviceGeneration);
    }

    synchronized void recordServiceDestroy(long serviceGeneration, long wallMs, long elapsedMs) {
        serviceDestroyCount++;
        lastServiceDestroyWallMs = wallMs;
        lastServiceDestroyElapsedMs = elapsedMs;
        lastDestroyedServiceGeneration = serviceGeneration;
        lastDestroyedServiceInstanceId = serviceInstanceIdFor(serviceGeneration);
    }

    synchronized void recordTaskRemoved(long serviceGeneration, long wallMs, long elapsedMs) {
        serviceTaskRemovedCount++;
        lastTaskRemovedWallMs = wallMs;
        lastTaskRemovedElapsedMs = elapsedMs;
        lastTaskRemovedServiceGeneration = serviceGeneration;
        lastTaskRemovedServiceInstanceId = serviceInstanceIdFor(serviceGeneration);
    }

    synchronized void recordMonitorLoopStart(long serviceGeneration, String reason) {
        if (!isCurrentServiceGeneration(serviceGeneration) || monitorLoopShutdown) {
            return;
        }
        monitorLoopStartCount++;
        monitorLoopActive = true;
        lastMonitorLoopStartReason = normalizeLoopReason(reason);
    }

    synchronized void recordMonitorLoopStop(long serviceGeneration, String reason) {
        if (!isCurrentServiceGeneration(serviceGeneration) || monitorLoopShutdown) {
            return;
        }
        monitorLoopStopCount++;
        monitorLoopActive = false;
        lastMonitorLoopStopReason = normalizeLoopReason(reason);
    }

    synchronized void recordMonitorLoopShutdown(long serviceGeneration, String reason) {
        if (!isCurrentServiceGeneration(serviceGeneration) || monitorLoopShutdown) {
            return;
        }
        monitorLoopShutdownCount++;
        monitorLoopActive = false;
        monitorLoopShutdown = true;
        lastMonitorLoopShutdownReason = normalizeLoopReason(reason);
    }

    synchronized void recordCallbackScheduled(long serviceGeneration, long scheduledElapsedMs,
            long dueElapsedMs) {
        if (!isCurrentServiceGeneration(serviceGeneration)) {
            return;
        }
        lastCallbackScheduledElapsedMs = scheduledElapsedMs;
        scheduledCallbackDueElapsedMs = dueElapsedMs;
        pendingCallbackDueElapsedMs = dueElapsedMs;
        callbackDeadlinePending = true;
    }

    synchronized void cancelPendingCallbackDeadline(long serviceGeneration) {
        if (!isCurrentServiceGeneration(serviceGeneration)) {
            return;
        }
        pendingCallbackDueElapsedMs = 0L;
        callbackDeadlinePending = false;
    }

    synchronized boolean recordMonitorStart(long serviceGeneration, long elapsedMs, long wallMs) {
        if (!isCurrentServiceGeneration(serviceGeneration)) {
            return false;
        }
        actualMonitorStartElapsedMs = elapsedMs;
        lastMonitorStartElapsedMs = elapsedMs;
        lastMonitorStartWallMs = wallMs;

        if (!callbackDeadlinePending) {
            return true;
        }

        callbackLatenessMs = Math.max(0L, elapsedMs - pendingCallbackDueElapsedMs);
        maxCallbackLatenessMs = Math.max(maxCallbackLatenessMs, callbackLatenessMs);
        if (callbackLatenessMs > 3_000L) {
            callbackLatenessOver3000Count++;
        }
        if (callbackLatenessMs > 5_000L) {
            callbackLatenessOver5000Count++;
        }
        pendingCallbackDueElapsedMs = 0L;
        callbackDeadlinePending = false;
        return true;
    }

    synchronized boolean recordMonitorEnd(long serviceGeneration, long elapsedMs, long wallMs) {
        if (!isCurrentServiceGeneration(serviceGeneration)) {
            return false;
        }
        lastMonitorEndElapsedMs = elapsedMs;
        lastMonitorEndWallMs = wallMs;
        return true;
    }

    private void resetCurrentServiceState() {
        monitorLoopActive = false;
        monitorLoopShutdown = false;
        monitorLoopStartCount = 0L;
        monitorLoopStopCount = 0L;
        monitorLoopShutdownCount = 0L;
        lastMonitorLoopStartReason = LOOP_REASON_OTHER;
        lastMonitorLoopStopReason = LOOP_REASON_OTHER;
        lastMonitorLoopShutdownReason = LOOP_REASON_OTHER;

        lastMonitorStartElapsedMs = 0L;
        lastMonitorStartWallMs = 0L;
        lastMonitorEndElapsedMs = 0L;
        lastMonitorEndWallMs = 0L;
        lastCallbackScheduledElapsedMs = 0L;
        scheduledCallbackDueElapsedMs = 0L;
        pendingCallbackDueElapsedMs = 0L;
        callbackDeadlinePending = false;
        actualMonitorStartElapsedMs = 0L;
        callbackLatenessMs = 0L;
        maxCallbackLatenessMs = 0L;
        callbackLatenessOver3000Count = 0L;
        callbackLatenessOver5000Count = 0L;
    }

    private String serviceInstanceIdFor(long serviceGeneration) {
        return processPid + "-" + serviceGeneration;
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(
                processPid,
                processInstanceStartedElapsedMs,
                processInstanceStartedWallMs,
                serviceInstanceId,
                currentServiceGeneration,
                serviceCreateCount,
                serviceStartCommandCount,
                serviceDestroyCount,
                serviceTaskRemovedCount,
                serviceInstanceStartedElapsedMs,
                serviceInstanceStartedWallMs,
                lastStartCommandElapsedMs,
                lastStartCommandWallMs,
                lastStartCommandAction,
                lastStartCommandServiceInstanceId,
                lastStartCommandServiceGeneration,
                lastServiceDestroyElapsedMs,
                lastServiceDestroyWallMs,
                lastDestroyedServiceInstanceId,
                lastDestroyedServiceGeneration,
                lastTaskRemovedElapsedMs,
                lastTaskRemovedWallMs,
                lastTaskRemovedServiceInstanceId,
                lastTaskRemovedServiceGeneration,
                monitorLoopActive,
                monitorLoopShutdown,
                monitorLoopStartCount,
                monitorLoopStopCount,
                monitorLoopShutdownCount,
                lastMonitorLoopStartReason,
                lastMonitorLoopStopReason,
                lastMonitorLoopShutdownReason,
                lastMonitorStartElapsedMs,
                lastMonitorStartWallMs,
                lastMonitorEndElapsedMs,
                lastMonitorEndWallMs,
                lastCallbackScheduledElapsedMs,
                scheduledCallbackDueElapsedMs,
                callbackDeadlinePending,
                actualMonitorStartElapsedMs,
                callbackLatenessMs,
                maxCallbackLatenessMs,
                callbackLatenessOver3000Count,
                callbackLatenessOver5000Count);
    }

    private static String normalizeAction(String action) {
        if (ACTION_START.equals(action)
                || ACTION_RELOAD.equals(action)
                || ACTION_STOP.equals(action)
                || ACTION_BEGIN_REST.equals(action)
                || ACTION_COMPLETE_REST.equals(action)
                || ACTION_CANCEL_REST.equals(action)
                || ACTION_EMERGENCY.equals(action)) {
            return action;
        }
        return ACTION_UNKNOWN;
    }

    private static String normalizeLoopReason(String reason) {
        if (LOOP_REASON_ENGINE_COMMAND_START.equals(reason)
                || LOOP_REASON_USER_STOP.equals(reason)
                || LOOP_REASON_SERVICE_DESTROY.equals(reason)
                || LOOP_REASON_UNSUPPORTED_RUNTIME.equals(reason)) {
            return reason;
        }
        return LOOP_REASON_OTHER;
    }

    static final class Snapshot {
        final int processPid;
        final long processInstanceStartedElapsedMs;
        final long processInstanceStartedWallMs;
        final String serviceInstanceId;
        final long currentServiceGeneration;
        final long serviceCreateCount;
        final long serviceStartCommandCount;
        final long serviceDestroyCount;
        final long serviceTaskRemovedCount;
        final long serviceInstanceStartedElapsedMs;
        final long serviceInstanceStartedWallMs;
        final long lastStartCommandElapsedMs;
        final long lastStartCommandWallMs;
        final String lastStartCommandAction;
        final String lastStartCommandServiceInstanceId;
        final long lastStartCommandServiceGeneration;
        final long lastServiceDestroyElapsedMs;
        final long lastServiceDestroyWallMs;
        final String lastDestroyedServiceInstanceId;
        final long lastDestroyedServiceGeneration;
        final long lastTaskRemovedElapsedMs;
        final long lastTaskRemovedWallMs;
        final String lastTaskRemovedServiceInstanceId;
        final long lastTaskRemovedServiceGeneration;
        final boolean monitorLoopActive;
        final boolean monitorLoopShutdown;
        final long monitorLoopStartCount;
        final long monitorLoopStopCount;
        final long monitorLoopShutdownCount;
        final String lastMonitorLoopStartReason;
        final String lastMonitorLoopStopReason;
        final String lastMonitorLoopShutdownReason;
        final long lastMonitorStartElapsedMs;
        final long lastMonitorStartWallMs;
        final long lastMonitorEndElapsedMs;
        final long lastMonitorEndWallMs;
        final long lastCallbackScheduledElapsedMs;
        final long scheduledCallbackDueElapsedMs;
        final boolean callbackDeadlinePending;
        final long actualMonitorStartElapsedMs;
        final long callbackLatenessMs;
        final long maxCallbackLatenessMs;
        final long callbackLatenessOver3000Count;
        final long callbackLatenessOver5000Count;

        Snapshot(int processPid, long processInstanceStartedElapsedMs,
                long processInstanceStartedWallMs, String serviceInstanceId,
                long currentServiceGeneration, long serviceCreateCount,
                long serviceStartCommandCount,
                long serviceDestroyCount, long serviceTaskRemovedCount,
                long serviceInstanceStartedElapsedMs, long serviceInstanceStartedWallMs,
                long lastStartCommandElapsedMs, long lastStartCommandWallMs,
                String lastStartCommandAction, String lastStartCommandServiceInstanceId,
                long lastStartCommandServiceGeneration, long lastServiceDestroyElapsedMs,
                long lastServiceDestroyWallMs, String lastDestroyedServiceInstanceId,
                long lastDestroyedServiceGeneration, long lastTaskRemovedElapsedMs,
                long lastTaskRemovedWallMs, String lastTaskRemovedServiceInstanceId,
                long lastTaskRemovedServiceGeneration, boolean monitorLoopActive,
                boolean monitorLoopShutdown, long monitorLoopStartCount,
                long monitorLoopStopCount, long monitorLoopShutdownCount,
                String lastMonitorLoopStartReason, String lastMonitorLoopStopReason,
                String lastMonitorLoopShutdownReason, long lastMonitorStartElapsedMs,
                long lastMonitorStartWallMs, long lastMonitorEndElapsedMs,
                long lastMonitorEndWallMs, long lastCallbackScheduledElapsedMs,
                long scheduledCallbackDueElapsedMs, boolean callbackDeadlinePending,
                long actualMonitorStartElapsedMs, long callbackLatenessMs,
                long maxCallbackLatenessMs, long callbackLatenessOver3000Count,
                long callbackLatenessOver5000Count) {
            this.processPid = processPid;
            this.processInstanceStartedElapsedMs = processInstanceStartedElapsedMs;
            this.processInstanceStartedWallMs = processInstanceStartedWallMs;
            this.serviceInstanceId = serviceInstanceId;
            this.currentServiceGeneration = currentServiceGeneration;
            this.serviceCreateCount = serviceCreateCount;
            this.serviceStartCommandCount = serviceStartCommandCount;
            this.serviceDestroyCount = serviceDestroyCount;
            this.serviceTaskRemovedCount = serviceTaskRemovedCount;
            this.serviceInstanceStartedElapsedMs = serviceInstanceStartedElapsedMs;
            this.serviceInstanceStartedWallMs = serviceInstanceStartedWallMs;
            this.lastStartCommandElapsedMs = lastStartCommandElapsedMs;
            this.lastStartCommandWallMs = lastStartCommandWallMs;
            this.lastStartCommandAction = lastStartCommandAction;
            this.lastStartCommandServiceInstanceId = lastStartCommandServiceInstanceId;
            this.lastStartCommandServiceGeneration = lastStartCommandServiceGeneration;
            this.lastServiceDestroyElapsedMs = lastServiceDestroyElapsedMs;
            this.lastServiceDestroyWallMs = lastServiceDestroyWallMs;
            this.lastDestroyedServiceInstanceId = lastDestroyedServiceInstanceId;
            this.lastDestroyedServiceGeneration = lastDestroyedServiceGeneration;
            this.lastTaskRemovedElapsedMs = lastTaskRemovedElapsedMs;
            this.lastTaskRemovedWallMs = lastTaskRemovedWallMs;
            this.lastTaskRemovedServiceInstanceId = lastTaskRemovedServiceInstanceId;
            this.lastTaskRemovedServiceGeneration = lastTaskRemovedServiceGeneration;
            this.monitorLoopActive = monitorLoopActive;
            this.monitorLoopShutdown = monitorLoopShutdown;
            this.monitorLoopStartCount = monitorLoopStartCount;
            this.monitorLoopStopCount = monitorLoopStopCount;
            this.monitorLoopShutdownCount = monitorLoopShutdownCount;
            this.lastMonitorLoopStartReason = lastMonitorLoopStartReason;
            this.lastMonitorLoopStopReason = lastMonitorLoopStopReason;
            this.lastMonitorLoopShutdownReason = lastMonitorLoopShutdownReason;
            this.lastMonitorStartElapsedMs = lastMonitorStartElapsedMs;
            this.lastMonitorStartWallMs = lastMonitorStartWallMs;
            this.lastMonitorEndElapsedMs = lastMonitorEndElapsedMs;
            this.lastMonitorEndWallMs = lastMonitorEndWallMs;
            this.lastCallbackScheduledElapsedMs = lastCallbackScheduledElapsedMs;
            this.scheduledCallbackDueElapsedMs = scheduledCallbackDueElapsedMs;
            this.callbackDeadlinePending = callbackDeadlinePending;
            this.actualMonitorStartElapsedMs = actualMonitorStartElapsedMs;
            this.callbackLatenessMs = callbackLatenessMs;
            this.maxCallbackLatenessMs = maxCallbackLatenessMs;
            this.callbackLatenessOver3000Count = callbackLatenessOver3000Count;
            this.callbackLatenessOver5000Count = callbackLatenessOver5000Count;
        }
    }
}
