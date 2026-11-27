package com.shrimpfarm.app.finance;

import static org.junit.Assert.*;
import org.junit.Test;

public class V17MigrationFlagTest {
    @Test public void flagNamesAreFrozen() {
        assertEquals("migrated_v17_income_items", V17Migration.FLAG_MIGRATED);
        assertEquals("v17_fixup_json", V17Migration.FLAG_FIXUP_JSON);
    }
}
