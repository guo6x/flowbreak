package com.flowbreak.app;

import static org.junit.Assert.*;

import android.os.Looper;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

/** Completion must not dereference a detached Activity or lose the true result. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeFlowRestCallbackTest {
    private NativeFlowPlugin plugin;
    private RecordingCall call;

    @Before public void setUp() {
        // No bridge or Activity is attached to this plugin instance.
        plugin = new NativeFlowPlugin();
        call = new RecordingCall();
    }

    @After public void tearDown() {
        plugin.handleOnDestroy();
    }

    @Test public void workerSuccessSurvivesActivityDetachment() throws Exception {
        JSObject response = new JSObject();
        response.put("graceUntil", 123L);
        onWorker(() -> plugin.restCompletionCallback(call).onSuccess(
                new NativeFlowRestCoordinator.Result(response, false)
        ));
        assertSame(response, call.response);
        assertNull(call.rejection);
        assertEquals(1, call.settlements);
        assertSame(Looper.getMainLooper(), call.resultLooper);
    }

    @Test public void deferredResultSurvivesActivityDetachment() throws Exception {
        onWorker(() -> plugin.restCompletionCallback(call).onDeferred());
        assertEquals(RestCompletionIntegrityGate.RETRYABLE_MESSAGE, call.rejection);
        assertEquals(1, call.settlements);
        assertSame(Looper.getMainLooper(), call.resultLooper);
    }

    @Test public void rejectionPreservesMessageAndCauseWithoutAnActivity() throws Exception {
        Exception error = new IllegalStateException("test failure");
        onWorker(() -> plugin.restCompletionCallback(call).onRejected("未完成", error));
        assertEquals("未完成", call.rejection);
        assertSame(error, call.error);
        assertEquals(1, call.settlements);
    }

    @Test public void rejectionWithoutCauseAlsoSettlesWithoutAnActivity() throws Exception {
        onWorker(() -> plugin.restCompletionCallback(call).onRejected("需要重试", null));
        assertEquals("需要重试", call.rejection);
        assertNull(call.error);
        assertEquals(1, call.settlements);
    }

    private void onWorker(Runnable callback) throws Exception {
        java.util.concurrent.FutureTask<Void> task = new java.util.concurrent.FutureTask<>(callback, null);
        Thread worker = new Thread(task, "flowbreak-rest-callback-test");
        worker.start();
        task.get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(0, call.settlements);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private static final class RecordingCall extends PluginCall {
        JSObject response;
        String rejection;
        Exception error;
        int settlements;
        Looper resultLooper;

        RecordingCall() { super(null, "NativeFlow", "test", "completeRestAndUnlock", new JSObject()); }
        @Override public void resolve(JSObject data) { response = data; record(); }
        @Override public void reject(String message) { rejection = message; record(); }
        @Override public void reject(String message, Exception cause) {
            rejection = message; error = cause; record();
        }
        private void record() { settlements++; resultLooper = Looper.myLooper(); }
    }
}
