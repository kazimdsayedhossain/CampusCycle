package bd.ac.kuet.campuscycle.domain;

import java.util.List;

/** Canonical KUET hub names + coordinates. Single source to prevent name drift. */
public final class CampusHubs {
    public record Hub(String name, double lat, double lng) {}

    public static final List<Hub> ALL = List.of(
            new Hub("KUET Central Library", 22.9009, 89.5016),
            new Hub("Student Welfare Centre", 22.9017, 89.5030),
            new Hub("KUET Main Gate", 22.8987, 89.4981),
            new Hub("Hall Gate", 22.9045, 89.5060),
            new Hub("Academic Building", 22.9015, 89.5010));

    private CampusHubs() {}

    public static List<String> names() {
        return ALL.stream().map(Hub::name).toList();
    }

    public static Hub nearest(double lat, double lng) {
        Hub best = ALL.get(0);
        double bestD = Double.MAX_VALUE;
        for (Hub h : ALL) {
            double d = Math.hypot(h.lat() - lat, h.lng() - lng);
            if (d < bestD) {
                bestD = d;
                best = h;
            }
        }
        return best;
    }

    public static Hub byName(String name) {
        if (name == null) return ALL.get(0);
        for (Hub h : ALL) {
            if (h.name().equalsIgnoreCase(name.trim())) return h;
        }
        return ALL.get(0);
    }
}
