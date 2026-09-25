package bd.ac.kuet.campuscycle.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class CycleMapPointTest {
    @Test
    void acceptsValidKuetCoordinate() {
        assertDoesNotThrow(() -> new CycleMapPoint("cycle-1", "Library cycle", "Central Library", 22.9009, 89.5016));
    }

    @Test
    void rejectsInvalidCoordinate() {
        assertThrows(IllegalArgumentException.class, () -> new CycleMapPoint("cycle-1", "Library cycle", "Central Library", 91, 89.5016));
    }
}
