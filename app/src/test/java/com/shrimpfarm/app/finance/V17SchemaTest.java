package com.shrimpfarm.app.finance;

import static org.junit.Assert.*;
import java.lang.reflect.*;
import org.junit.Test;

public class V17SchemaTest {
    @Test public void tableNamesAndVersionAreFrozen() throws Exception {
        assertEquals("fin_income_item", V17Schema.TABLE_FIN_INCOME_ITEM);
        assertEquals("shed_archive", V17Schema.TABLE_SHED_ARCHIVE);
        assertEquals("fin_doc_seq", V17Schema.TABLE_FIN_DOC_SEQ);
        Field v = Class.forName("com.shrimpfarm.app.DatabaseHelper")
                .getDeclaredField("DATABASE_VERSION");
        v.setAccessible(true);
        assertEquals(17, v.getInt(null));
    }
}
