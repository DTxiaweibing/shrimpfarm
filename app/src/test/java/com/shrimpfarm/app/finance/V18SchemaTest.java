package com.shrimpfarm.app.finance;

import static org.junit.Assert.*;
import java.lang.reflect.Field;
import org.junit.Test;

public class V18SchemaTest {
    @Test
    public void versionAndTableNamesAreFrozen() throws Exception {
        assertEquals("fin_agent_customer", V18Schema.TABLE_FIN_AGENT_CUSTOMER);
        assertEquals("fin_settlement", V18Schema.TABLE_FIN_SETTLEMENT);
        assertEquals("fin_park", V18Schema.TABLE_FIN_PARK);
        Field v = Class.forName("com.shrimpfarm.app.DatabaseHelper")
                .getDeclaredField("DATABASE_VERSION");
        v.setAccessible(true);
        assertEquals(18, v.getInt(null));
    }
}
