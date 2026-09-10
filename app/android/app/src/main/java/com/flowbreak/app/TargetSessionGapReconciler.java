package com.flowbreak.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Replays a verified UsageEvents timeline across a delayed monitor callback.
 *
 * <p>This class is deliberately independent of Android, Room, preferences and
 * the Service.  The caller supplies the checkpoint and the ordered events;
 * the result contains only intervals that can be justified by that timeline.
 * The final live tail is excluded so the normal ten-second live clamp remains
 * the only authority for that tail.</p>
 */
public final class TargetSessionGapReconciler {
    public static final long LIVE_TAIL_MS = 10_000L;

    /** Event kinds after the Android UsageEvents adapter has classified them. */
    public enum EventType {
        FOREGROUND,
        BACKGROUND,
        SCREEN_INTERACTIVE,
        SCREEN_NON_INTERACTIVE,
        KEYGUARD_SHOWN,
        KEYGUARD_HIDDEN
    }

    public enum Status {
        SUCCESS,
        INCOMPLETE,
        SKIPPED
    }

    /** A single ordered event. Equal timestamps retain the caller's order. */
    public static final class Event {
        public final EventType type;
        public final String packageName;
        public final String className;
        public final long timestamp;

        public Event(EventType type, String packageName, String className, long timestamp) {
            this.type = type;
            this.packageName = packageName;
            this.className = className;
            this.timestamp = timestamp;
        }

        public static Event foreground(String packageName, String className, long timestamp) {
            return new Event(EventType.FOREGROUND, packageName, className, timestamp);
        }

        public static Event background(String packageName, String className, long timestamp) {
            return new Event(EventType.BACKGROUND, packageName, className, timestamp);
        }

        public static Event screenInteractive(long timestamp) {
            return new Event(EventType.SCREEN_INTERACTIVE, "", null, timestamp);
        }

        public static Event screenNonInteractive(long timestamp) {
            return new Event(EventType.SCREEN_NON_INTERACTIVE, "", null, timestamp);
        }

        public static Event keyguardShown(long timestamp) {
            return new Event(EventType.KEYGUARD_SHOWN, "", null, timestamp);
        }

        public static Event keyguardHidden(long timestamp) {
            return new Event(EventType.KEYGUARD_HIDDEN, "", null, timestamp);
        }
    }

    /** Checkpoint captured immediately after the last completed monitor tick. */
    public static final class Input {
        public final long gapStartWallMs;
        public final long safeEndWallMs;
        public final String startingForegroundPackage;
        public final boolean startingTargetActive;
        public final Boolean startingInteractionAvailable;
        public final Set<String> targetApps;
        public final List<Event> events;

        public Input(
                long gapStartWallMs,
                long safeEndWallMs,
                String startingForegroundPackage,
                boolean startingTargetActive,
                Boolean startingInteractionAvailable,
                Set<String> targetApps,
                List<Event> events
        ) {
            this.gapStartWallMs = gapStartWallMs;
            this.safeEndWallMs = safeEndWallMs;
            this.startingForegroundPackage = startingForegroundPackage;
            this.startingTargetActive = startingTargetActive;
            this.startingInteractionAvailable = startingInteractionAvailable;
            this.targetApps = targetApps == null
                    ? null
                    : Collections.unmodifiableSet(new LinkedHashSet<>(targetApps));
            this.events = events == null
                    ? null
                    : Collections.unmodifiableList(new ArrayList<>(events));
        }
    }

    /** A state interval, including non-target intervals needed for reset rules. */
    public static final class TimelineSegment {
        public final long startWallMs;
        public final long endWallMs;
        public final String foregroundPackage;
        public final boolean targetActive;
        public final boolean interactionAvailable;

        TimelineSegment(
                long startWallMs,
                long endWallMs,
                String foregroundPackage,
                boolean targetActive,
                boolean interactionAvailable
        ) {
            this.startWallMs = startWallMs;
            this.endWallMs = endWallMs;
            this.foregroundPackage = foregroundPackage;
            this.targetActive = targetActive;
            this.interactionAvailable = interactionAvailable;
        }

        public long durationMs() {
            return Math.max(0L, endWallMs - startWallMs);
        }

        public boolean contributesUsage() {
            return targetActive && interactionAvailable && !foregroundPackage.isEmpty();
        }
    }

    /** State change retained for service-side replay through BlockStateMachine. */
    public static final class Transition {
        public final long timestamp;
        public final EventType eventType;
        public final String foregroundPackage;
        public final boolean targetActive;
        public final boolean interactionAvailable;

        Transition(
                long timestamp,
                EventType eventType,
                String foregroundPackage,
                boolean targetActive,
                boolean interactionAvailable
        ) {
            this.timestamp = timestamp;
            this.eventType = eventType;
            this.foregroundPackage = foregroundPackage;
            this.targetActive = targetActive;
            this.interactionAvailable = interactionAvailable;
        }
    }

    public static final class Result {
        public final Status status;
        public final List<TimelineSegment> segments;
        public final List<Transition> transitions;
        public final Map<String, Long> usageMsByTargetPackage;
        public final long coverageMs;
        public final long historicalTargetMsRecovered;
        public final String finalForegroundPackage;
        public final boolean finalTargetActive;
        public final boolean finalInteractionAvailable;

        private Result(
                Status status,
                List<TimelineSegment> segments,
                List<Transition> transitions,
                Map<String, Long> usageMsByTargetPackage,
                long coverageMs,
                long historicalTargetMsRecovered,
                String finalForegroundPackage,
                boolean finalTargetActive,
                boolean finalInteractionAvailable
        ) {
            this.status = status;
            this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
            this.transitions = Collections.unmodifiableList(new ArrayList<>(transitions));
            this.usageMsByTargetPackage = Collections.unmodifiableMap(
                    new LinkedHashMap<>(usageMsByTargetPackage)
            );
            this.coverageMs = coverageMs;
            this.historicalTargetMsRecovered = historicalTargetMsRecovered;
            this.finalForegroundPackage = finalForegroundPackage;
            this.finalTargetActive = finalTargetActive;
            this.finalInteractionAvailable = finalInteractionAvailable;
        }

        static Result incomplete(Input input) {
            String packageName = input == null || input.startingForegroundPackage == null
                    ? ""
                    : input.startingForegroundPackage;
            boolean target = input != null && input.startingTargetActive;
            boolean interaction = input != null
                    && Boolean.TRUE.equals(input.startingInteractionAvailable);
            return new Result(
                    Status.INCOMPLETE,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    0L,
                    0L,
                    packageName,
                    target,
                    interaction
            );
        }
    }

    /**
     * Reconcile only the closed, safe portion of the callback gap.
     *
     * <p>The input's event list must already be in chronological order.  A
     * timestamp tie is intentionally not re-sorted: query order is part of the
     * evidence and is preserved.</p>
     */
    public static Result reconcile(Input input) {
        if (!isValidInput(input)) return Result.incomplete(input);

        String startingPackage = input.startingForegroundPackage == null
                ? ""
                : input.startingForegroundPackage;
        if (input.startingTargetActive && isAmbiguousTarget(startingPackage, input.targetApps)) {
            return Result.incomplete(input);
        }

        ForegroundEventState foreground = new ForegroundEventState();
        foreground.seed(startingPackage, input.gapStartWallMs);
        boolean targetActive = input.startingTargetActive;
        boolean screenInteractive = input.startingInteractionAvailable;
        boolean keyguardVisible = false;
        long previousEventTimestamp = Long.MIN_VALUE;
        long segmentStart = input.gapStartWallMs;
        List<TimelineSegment> segments = new ArrayList<>();
        List<Transition> transitions = new ArrayList<>();
        Map<String, Long> usage = new LinkedHashMap<>();

        for (Event event : input.events) {
            if (event == null || event.type == null || event.timestamp < previousEventTimestamp) {
                return Result.incomplete(input);
            }
            previousEventTimestamp = event.timestamp;
            if (event.timestamp <= input.gapStartWallMs) continue;
            if (event.timestamp > input.safeEndWallMs) break;

            if (isForegroundOrBackground(event.type)
                    && (event.packageName == null || event.packageName.isEmpty())) {
                return Result.incomplete(input);
            }
            if (isAmbiguousTarget(event.packageName, input.targetApps)) {
                return Result.incomplete(input);
            }

            appendSegment(
                    segments,
                    usage,
                    segmentStart,
                    event.timestamp,
                    foreground.getForegroundPackage(),
                    targetActive,
                    screenInteractive && !keyguardVisible
            );

            foreground.accept(
                    event.packageName,
                    event.className,
                    event.type == EventType.FOREGROUND,
                    event.type == EventType.BACKGROUND,
                    event.timestamp
            );

            if (event.type == EventType.FOREGROUND || event.type == EventType.BACKGROUND) {
                String nextPackage = foreground.getForegroundPackage();
                if (event.type == EventType.FOREGROUND || nextPackage.isEmpty()) {
                    targetActive = isTargetPackage(nextPackage, input.targetApps);
                } else if (!nextPackage.equals(event.packageName)) {
                    // A stale activity background event did not change the
                    // foreground package or the existing target decision.
                    targetActive = targetActive && isTargetPackage(nextPackage, input.targetApps);
                } else {
                    targetActive = isTargetPackage(nextPackage, input.targetApps);
                }
            } else if (event.type == EventType.SCREEN_INTERACTIVE) {
                screenInteractive = true;
            } else if (event.type == EventType.SCREEN_NON_INTERACTIVE) {
                screenInteractive = false;
            } else if (event.type == EventType.KEYGUARD_SHOWN) {
                keyguardVisible = true;
            } else if (event.type == EventType.KEYGUARD_HIDDEN) {
                keyguardVisible = false;
            }

            transitions.add(new Transition(
                    event.timestamp,
                    event.type,
                    foreground.getForegroundPackage(),
                    targetActive,
                    screenInteractive && !keyguardVisible
            ));
            segmentStart = event.timestamp;
        }

        appendSegment(
                segments,
                usage,
                segmentStart,
                input.safeEndWallMs,
                foreground.getForegroundPackage(),
                targetActive,
                screenInteractive && !keyguardVisible
        );

        long recovered = 0L;
        for (Long duration : usage.values()) recovered += duration;
        return new Result(
                Status.SUCCESS,
                segments,
                transitions,
                usage,
                input.safeEndWallMs - input.gapStartWallMs,
                recovered,
                foreground.getForegroundPackage(),
                targetActive,
                screenInteractive && !keyguardVisible
        );
    }

    private static boolean isValidInput(Input input) {
        if (input == null
                || input.gapStartWallMs < 0L
                || input.safeEndWallMs <= input.gapStartWallMs
                || input.startingForegroundPackage == null
                || input.startingInteractionAvailable == null
                || input.targetApps == null
                || input.events == null) {
            return false;
        }
        if (input.startingTargetActive && input.startingForegroundPackage.isEmpty()) return false;
        for (String target : input.targetApps) {
            if (target == null || target.isEmpty()) return false;
        }
        if (!input.startingForegroundPackage.isEmpty()
                && input.startingTargetActive
                && !input.targetApps.contains(input.startingForegroundPackage)) {
            return false;
        }
        return true;
    }

    private static boolean isForegroundOrBackground(EventType type) {
        return type == EventType.FOREGROUND || type == EventType.BACKGROUND;
    }

    private static boolean isTargetPackage(String packageName, Set<String> targetApps) {
        return packageName != null
                && !packageName.isEmpty()
                && targetApps.contains(packageName)
                && !isAmbiguousTarget(packageName, targetApps);
    }

    /** WeChat video-channel entry is not reconstructable from package events. */
    private static boolean isAmbiguousTarget(String packageName, Set<String> targetApps) {
        return "com.tencent.mm".equals(packageName) && targetApps.contains(packageName);
    }

    private static void appendSegment(
            List<TimelineSegment> segments,
            Map<String, Long> usage,
            long start,
            long end,
            String foregroundPackage,
            boolean targetActive,
            boolean interactionAvailable
    ) {
        if (end <= start) return;
        String packageName = foregroundPackage == null ? "" : foregroundPackage;
        TimelineSegment segment = new TimelineSegment(
                start,
                end,
                packageName,
                targetActive,
                interactionAvailable
        );
        segments.add(segment);
        if (!segment.contributesUsage()) return;
        long duration = segment.durationMs();
        usage.put(packageName, usage.getOrDefault(packageName, 0L) + duration);
    }

    private TargetSessionGapReconciler() { }
}
