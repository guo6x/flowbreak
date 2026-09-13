package com.flowbreak.app;

/**
 * Pure evaluator for the hard prerequisites of core protection.
 *
 * <p>Runtime/service health is deliberately evaluated separately. This gate
 * only answers whether the configured core protection prerequisites are
 * present; it does not redefine {@code protectionRuntimeAvailable}.</p>
 */
public final class ProtectionPrerequisiteGate {
    public enum Reason {
        OK,
        UNSUPPORTED_DEVICE,
        MONITORING_DISABLED,
        NO_TARGETS,
        USAGE_ACCESS_MISSING,
        OVERLAY_MISSING
    }

    public static final class Result {
        public final Reason reason;

        private Result(Reason reason) {
            this.reason = reason;
        }

        public boolean isAllowed() {
            return reason == Reason.OK;
        }

        public String code() {
            return reason.name();
        }

        public String message() {
            switch (reason) {
                case UNSUPPORTED_DEVICE:
                    return "当前设备不支持可靠保护运行时";
                case MONITORING_DISABLED:
                    return "保护监控当前已暂停";
                case NO_TARGETS:
                    return "至少需要一个可阻断应用";
                case USAGE_ACCESS_MISSING:
                    return "缺少使用情况访问权限";
                case OVERLAY_MISSING:
                    return "缺少悬浮窗权限";
                case OK:
                default:
                    return "";
            }
        }
    }

    private ProtectionPrerequisiteGate() { }

    public static Result evaluate(
            boolean supportedRuntime,
            boolean monitoringEnabled,
            boolean hasTargets,
            boolean hasUsageStats,
            boolean hasOverlay
    ) {
        if (!supportedRuntime) return new Result(Reason.UNSUPPORTED_DEVICE);
        if (!monitoringEnabled) return new Result(Reason.MONITORING_DISABLED);
        if (!hasTargets) return new Result(Reason.NO_TARGETS);
        if (!hasUsageStats) return new Result(Reason.USAGE_ACCESS_MISSING);
        if (!hasOverlay) return new Result(Reason.OVERLAY_MISSING);
        return new Result(Reason.OK);
    }
}
