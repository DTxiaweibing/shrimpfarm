package com.shrimpfarm.app.finance;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class V18MigrationTest {
    @Test
    public void migrationFlagsAreStable() {
        assertEquals("migrated_v18_finance_arap", V18Migration.FLAG_MIGRATED);
    }
}
