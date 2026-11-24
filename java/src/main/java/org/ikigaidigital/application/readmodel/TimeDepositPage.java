package org.ikigaidigital.application.readmodel;

import java.util.List;

/**
 * One page of Time Deposits, each with its Withdrawals.
 *
 * @param items the Time Deposits on this page, ordered by id
 * @param page zero-based index of this page
 * @param size requested page size
 * @param totalItems number of Time Deposits across all pages
 */
public record TimeDepositPage(List<TimeDepositView> items, int page, int size, long totalItems) {

    public TimeDepositPage {
        items = List.copyOf(items);
    }

    public int totalPages() {
        return (int) ((totalItems + size - 1) / size);
    }
}
