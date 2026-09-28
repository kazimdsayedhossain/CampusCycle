package bd.ac.kuet.campuscycle.domain;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Single clock source for the app. Bangladesh has no DST, but developer
 * machines run in arbitrary zones — never use the system default (P-044, P-064).
 */
public final class CampusTime {

    public static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

    private CampusTime() {}

    public static ZonedDateTime now() {
        return ZonedDateTime.now(DHAKA);
    }
}
