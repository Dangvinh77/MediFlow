package com.mediflow.report.application.mapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.mediflow.report.domain.model.OperationalContribution;

/** One deterministic scope plan shared by live projection and isolated replay. */
public final class OperationalProjectionPlanner {
    private OperationalProjectionPlanner() { }

    public static List<OperationalContribution> ordered(List<OperationalContribution> facts) {
        return facts.stream().sorted(Comparator.comparing(OperationalContribution::metric)).toList();
    }

    public static List<ScopeDelta> scopes(List<OperationalContribution> accepted) {
        var deltas = new ArrayList<ScopeDelta>();
        for (var fact : accepted) {
            deltas.add(new ScopeDelta(fact, null));
            deltas.add(new ScopeDelta(fact, fact.departmentId()));
        }
        deltas.sort(Comparator.comparing((ScopeDelta delta) -> delta.fact().metricDate())
                .thenComparing(ScopeDelta::departmentId, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(delta -> delta.fact().metric()));
        return List.copyOf(deltas);
    }

    public record ScopeDelta(OperationalContribution fact, UUID departmentId) { }
}
