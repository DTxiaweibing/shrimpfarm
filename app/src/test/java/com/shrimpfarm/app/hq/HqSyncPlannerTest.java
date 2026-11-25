package com.shrimpfarm.app.hq;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HqSyncPlannerTest {

    @Test
    public void returnsMissingDatesWithinWindowThatExistRemotely() {
        List<String> local = Arrays.asList("2026-11-18", "2026-11-20");
        List<String> remote = Arrays.asList("2026-11-16", "2026-11-17", "2026-11-18",
                "2026-11-19", "2026-11-20");
        List<String> missing = HqSyncPlanner.missingDates("2026-11-20", local, remote, 5);
        assertEquals(Arrays.asList("2026-11-16", "2026-11-17", "2026-11-19"), missing);
    }

    @Test
    public void ignoresRemoteDatesOutsideWindow() {
        List<String> local = Collections.emptyList();
        List<String> remote = Arrays.asList("2026-11-01", "2026-11-19", "2026-11-20");
        List<String> missing = HqSyncPlanner.missingDates("2026-11-20", local, remote, 3);
        assertEquals(Arrays.asList("2026-11-19", "2026-11-20"), missing);
    }

    @Test
    public void ignoresDatesMissingRemotely() {
        List<String> local = Collections.emptyList();
        List<String> remote = Arrays.asList("2026-11-20"); // 19 has no remote data
        List<String> missing = HqSyncPlanner.missingDates("2026-11-20", local, remote, 3);
        assertEquals(Collections.singletonList("2026-11-20"), missing);
    }

    @Test
    public void returnsEmptyWhenAllLocal() {
        List<String> local = Arrays.asList("2026-11-19", "2026-11-20");
        List<String> remote = Arrays.asList("2026-11-19", "2026-11-20");
        assertTrue(HqSyncPlanner.missingDates("2026-11-20", local, remote, 3).isEmpty());
    }

    @Test
    public void handlesNullInputsAndMalformedToday() {
        assertTrue(HqSyncPlanner.missingDates(null, null, null, 365).isEmpty());
        assertTrue(HqSyncPlanner.missingDates("bad", Collections.emptyList(),
                Collections.emptyList(), 365).isEmpty());
    }
}
