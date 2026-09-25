package bd.ac.kuet.campuscycle.domain;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Abstract base class demonstrating OOP inheritance, encapsulation,
 * and template method pattern for domain entities.
 */
public abstract class BaseEntity {

    private final String id;
    private final ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    protected BaseEntity(String id, ZonedDateTime createdAt, ZonedDateTime updatedAt) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.createdAt = createdAt != null ? createdAt : ZonedDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    /**
     * Template validation method. Each subclass enforces its own domain invariants.
     *
     * @throws IllegalArgumentException if entity state is invalid
     */
    public abstract void validate() throws IllegalArgumentException;

    public String getId() {
        return id;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    protected void touchUpdated() {
        this.updatedAt = ZonedDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseEntity other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
