package com.example.wordtrigger;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StudyCacheTest {
    @Test
    public void streakAliveToday() {
        assertEquals(5, StudyCache.effective(5, "2026-10-14", "2026-10-14", "2026-10-13"));
    }

    @Test
    public void streakAliveIfLastWasYesterday() {
        assertEquals(5, StudyCache.effective(5, "2026-10-13", "2026-10-14", "2026-10-13"));
    }

    @Test
    public void streakResetsAfterMissedDay() {
        assertEquals(0, StudyCache.effective(5, "2026-10-12", "2026-10-14", "2026-10-13"));
    }

    @Test
    public void noDateMeansNoStreak() {
        assertEquals(0, StudyCache.effective(5, null, "2026-10-14", "2026-10-13"));
    }
}
