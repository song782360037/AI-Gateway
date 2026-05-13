package com.code.aigateway.core.stats;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveRequestTrackerTest {

    private final ActiveRequestTracker tracker = new ActiveRequestTracker();

    @AfterEach
    void tearDown() {
        tracker.shutdown();
    }

    @Test
    void registerAndUpdateRequestInfo_recordsModelAndStream() {
        tracker.register("cid-1", "10.0.0.1", null, null);

        tracker.updateRequestInfo("cid-1", "gpt-4o", true);

        ActiveRequestTracker.ActiveRequest request = tracker.getActiveRequests().getFirst();
        assertEquals("gpt-4o", request.getModel());
        assertTrue(request.isStream());
    }

    @Test
    void getActiveClientCount_ignoresBlankSourceIp() {
        tracker.register("cid-1", null, null, null);
        tracker.register("cid-2", "", null, null);
        tracker.register("cid-3", "10.0.0.1", null, null);
        tracker.register("cid-4", "10.0.0.1", null, null);

        assertEquals(1, tracker.getActiveClientCount());
    }

    @Test
    void remove_deletesActiveRequest() {
        tracker.register("cid-1", "10.0.0.1", "gpt-4o", false);

        tracker.remove("cid-1");

        assertEquals(0, tracker.getActiveCount());
        assertFalse(tracker.getActiveGroupByProviderModel().containsKey("pending|pending"));
    }
}
