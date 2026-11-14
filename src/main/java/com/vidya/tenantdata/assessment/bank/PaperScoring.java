package com.vidya.tenantdata.assessment.bank;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Paper total from item marks, honouring "attempt any N of M" sections: in such a section only the N best-scoring
 * questions count (a case-based question counts once, as the sum of its parts).
 */
public final class PaperScoring {

    private PaperScoring() {
    }

    /** @param unitId the top-level question the item belongs to (itself, or its case-based passage) */
    public record ItemMark(int section, Integer sectionAttempt, UUID unitId, BigDecimal marks) {
    }

    public static BigDecimal total(List<ItemMark> items) {
        Map<Integer, Map<UUID, BigDecimal>> units = new LinkedHashMap<>();
        Map<Integer, Integer> attempts = new LinkedHashMap<>();
        for (ItemMark i : items) {
            units.computeIfAbsent(i.section(), k -> new LinkedHashMap<>())
                    .merge(i.unitId(), i.marks() == null ? BigDecimal.ZERO : i.marks(), BigDecimal::add);
            if (i.sectionAttempt() != null) {
                attempts.put(i.section(), i.sectionAttempt());
            }
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Integer, Map<UUID, BigDecimal>> e : units.entrySet()) {
            List<BigDecimal> marks = new ArrayList<>(e.getValue().values());
            marks.sort(Collections.reverseOrder());
            int n = attempts.containsKey(e.getKey()) ? Math.min(attempts.get(e.getKey()), marks.size()) : marks.size();
            for (int k = 0; k < n; k++) {
                total = total.add(marks.get(k));
            }
        }
        return total;
    }

    public static List<ItemMark> of(List<PaperService.Leaf> leaves, Map<UUID, BigDecimal> marksByItem) {
        return leaves.stream().map(l -> new ItemMark(l.sectionPosition(), l.sectionAttempt(),
                l.item().getParentItemId() != null ? l.item().getParentItemId() : l.item().getId(), marksByItem.get(l.item().getId()))).toList();
    }
}
