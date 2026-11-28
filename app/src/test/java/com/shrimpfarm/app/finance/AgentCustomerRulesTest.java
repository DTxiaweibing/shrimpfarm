package com.shrimpfarm.app.finance;

import static org.junit.Assert.*;

import org.junit.Test;

import com.shrimpfarm.app.finance.FinanceDb.FinAgentCustomer;
import com.shrimpfarm.app.finance.FinanceDb.FinSupplier;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AgentCustomerRulesTest {

    private static FinSupplier customer(long id, String name) {
        FinSupplier s = new FinSupplier();
        s.id = id;
        s.name = name;
        s.personType = FinanceDb.PERSON_CUSTOMER;
        return s;
    }

    private static FinAgentCustomer link(long customerId) {
        FinAgentCustomer r = new FinAgentCustomer();
        r.customerId = customerId;
        return r;
    }

    @Test public void agentRowDetectedByScreenTypeOrRecordType() {
        assertTrue(AgentCustomerRules.isAgentRow(FinanceDb.PERSON_AGENT, FinanceDb.PERSON_SUPPLIER));
        assertTrue(AgentCustomerRules.isAgentRow(FinanceDb.PERSON_SUPPLIER, FinanceDb.PERSON_AGENT));
        assertFalse(AgentCustomerRules.isAgentRow(FinanceDb.PERSON_SUPPLIER, FinanceDb.PERSON_CUSTOMER));
        assertFalse(AgentCustomerRules.isAgentRow(FinanceDb.PERSON_EMPLOYEE, FinanceDb.PERSON_EMPLOYEE));
    }

    @Test public void customerNameIsTrimmedAndBlankRejected() {
        assertFalse(AgentCustomerRules.validName(null));
        assertFalse(AgentCustomerRules.validName("  "));
        assertTrue(AgentCustomerRules.validName("  客户甲  "));
    }

    @Test public void linkedIdsCollectOnlyValidRelations() {
        List<FinAgentCustomer> links = Arrays.asList(link(3), link(9));
        Set<Long> ids = AgentCustomerRules.linkedCustomerIds(links);
        assertEquals(new HashSet<>(Arrays.asList(3L, 9L)), ids);
        assertTrue(AgentCustomerRules.linkedCustomerIds(Collections.<FinAgentCustomer>emptyList()).isEmpty());
    }

    @Test public void linkableCustomersExcludeAlreadyLinkedAndInvalidRows() {
        List<FinSupplier> customers = Arrays.asList(customer(3, "甲"), customer(4, "乙"), customer(5, "丙"));
        Set<Long> linked = new HashSet<>(Collections.singletonList(4L));
        assertEquals(Arrays.asList("甲", "丙"), names(AgentCustomerRules.linkableCustomers(customers, linked)));
        assertEquals(Arrays.asList("甲", "乙", "丙"),
                names(AgentCustomerRules.linkableCustomers(customers, Collections.<Long>emptySet())));
    }

    @Test public void missingCustomerUsesPlaceholder() {
        assertEquals("甲", AgentCustomerRules.displayName(customer(3, "甲"), "已删除"));
        assertEquals("已删除", AgentCustomerRules.displayName(null, "已删除"));
        assertEquals("已删除", AgentCustomerRules.displayName(customer(3, "   "), "已删除"));
    }

    @Test public void existingCustomerIdPreventsDuplicateQuickCreate() {
        List<FinSupplier> customers = Arrays.asList(customer(3, "甲"), customer(4, "乙"));
        assertEquals(3L, AgentCustomerRules.existingCustomerId(customers, "  甲  "));
        assertEquals(0L, AgentCustomerRules.existingCustomerId(customers, "丙"));
        assertEquals(0L, AgentCustomerRules.existingCustomerId(customers, "  "));
    }

    private static List<String> names(List<FinSupplier> suppliers) {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (FinSupplier s : suppliers) result.add(s.name);
        return result;
    }
}
