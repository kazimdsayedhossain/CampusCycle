package bd.ac.kuet.campuscycle.domain;

import java.util.List;

/** Canonical KUET hub names + coordinates. Single source to prevent name drift. */
public final class CampusHubs {

    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(CampusHubs.class.getName());

    /** Mean earth radius in metres (haversine). */
    private static final double EARTH_RADIUS_M = 6_371_000.0;
    public record Hub(String name, double lat, double lng) {}

    public static final List<Hub> ALL = List.of(
            new Hub("KUET Central Mosque", 22.9009, 89.5016),
            new Hub("Student Welfare Centre", 22.9017, 89.5030),
            new Hub("KUET Main Gate", 22.8987, 89.4981),
            new Hub("Hall Gate", 22.9045, 89.5060),
            new Hub("Academic Building", 22.9015, 89.5010));

    private CampusHubs() {}

    /** Cached hub names; previously allocated a fresh list per call (P-120). */
    private static final List<String> NAMES = ALL.stream().map(Hub::name).toList();

    public static List<String> names() {
        return NAMES;
    }

    /** Haversine distance in metres. Euclidean degrees understate
     * east-west distance by ~8% at KUET's latitude (P-143). */
    public static double distanceMetres(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }

    public static Hub nearest(double lat, double lng) {
        Hub best = ALL.get(0);
        double bestD = Double.MAX_VALUE;
        for (Hub h : ALL) {
            double d = distanceMetres(lat, lng, h.lat(), h.lng());
            if (d < bestD) {
                bestD = d;
                best = h;
            }
        }
        return best;
    }

    /**
     * Looks up a hub by name. Unknown names no longer silently become
     * hub 0 (P-143): use the Optional and fail visibly at the call site.
     */
    public static java.util.Optional<Hub> findByName(String name) {
        if (name == null) return java.util.Optional.empty();
        String trimmed = name.trim();
        for (Hub h : ALL) {
            if (h.name().equalsIgnoreCase(trimmed)) return java.util.Optional.of(h);
        }
        // Legacy alias, kept explicit and logged.
        if (trimmed.equalsIgnoreCase("Central Library") || trimmed.equalsIgnoreCase("KUET Central Library")) {
            LOGGER.warning("Legacy hub alias used: '" + trimmed + "' -> '" + ALL.get(0).name() + "'");
            return java.util.Optional.of(ALL.get(0));
        }
        return java.util.Optional.empty();
    }

    /**
     * Legacy lookup kept for compatibility. Logs a warning and falls back
     * to hub 0 so existing flows keep working; new code must use findByName.
     */
    public static Hub byName(String name) {
        return findByName(name).orElseGet(() -> {
            LOGGER.warning("Unknown hub name '" + name + "', defaulting to " + ALL.get(0).name());
            return ALL.get(0);
        });
    }
}
