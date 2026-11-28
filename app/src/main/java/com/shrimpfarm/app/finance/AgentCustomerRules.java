package com.shrimpfarm.app.finance;

import com.shrimpfarm.app.finance.FinanceDb.FinAgentCustomer;
import com.shrimpfarm.app.finance.FinanceDb.FinSupplier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure rules for A3 agent-customer association. No Android or DB dependency.
 */
public final class AgentCustomerRules {

    private AgentCustomerRules() {
    }

    /** Agent rows are detected by the screen filter or by the record's own type. */
    public static boolean isAgentRow(int filterType, int personType) {
        return filterType == FinanceDb.PERSON_AGENT || personType == FinanceDb.PERSON_AGENT;
    }

    public static boolean validName(String name) {
        return name != null && !name.trim().isEmpty();
    }

    public static Set<Long> linkedCustomerIds(List<FinAgentCustomer> links) {
        Set<Long> ids = new HashSet<>();
        if (links == null) return ids;
        for (FinAgentCustomer link : links) {
            if (link != null && link.customerId > 0) ids.add(link.customerId);
        }
        return ids;
    }

    /** Candidates already linked to this agent are omitted so no duplicate link is attempted. */
    public static List<FinSupplier> linkableCustomers(List<FinSupplier> customers, Set<Long> linkedIds) {
        List<FinSupplier> result = new ArrayList<>();
        if (customers == null) return result;
        Set<Long> linked = linkedIds == null ? new HashSet<Long>() : linkedIds;
        for (FinSupplier c : customers) {
            if (c != null && c.id > 0 && !linked.contains(c.id)) result.add(c);
        }
        return result;
    }

    /** Missing or deleted customer records get a placeholder instead of crashing the list. */
    public static String displayName(FinSupplier customer, String placeholder) {
        if (customer == null || !validName(customer.name)) return placeholder;
        return customer.name.trim();
    }

    /** Quick-create reuses a same-named customer instead of inserting a duplicate. */
    public static long existingCustomerId(List<FinSupplier> customers, String name) {
        if (!validName(name)) return 0;
        String key = name.trim();
        if (customers == null) return 0;
        for (FinSupplier c : customers) {
            if (c != null && c.id > 0 && key.equals(c.name)) return c.id;
        }
        return 0;
    }
}
