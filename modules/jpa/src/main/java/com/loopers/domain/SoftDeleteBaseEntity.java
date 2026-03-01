package com.loopers.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import java.time.ZonedDateTime;

/**
 * Soft Delete 전용 베이스 엔티티 (deletedAt + delete() + restore()).
 * Soft Delete를 사용하는 엔티티(UserEntity, BrandEntity, ProductEntity)가 상속한다.
 */
@MappedSuperclass
@Getter
public abstract class SoftDeleteBaseEntity extends BaseEntity {

    @Column(name = "deleted_at")
    private ZonedDateTime deletedAt;

    protected SoftDeleteBaseEntity() {
    }

    protected SoftDeleteBaseEntity(Long id) {
        super(id);
    }

    /**
     * delete 연산은 멱등하게 동작할 수 있도록 한다. (삭제된 엔티티를 다시 삭제해도 동일한 결과가 나오도록)
     */
    public void delete() {
        if (this.deletedAt == null) {
            this.deletedAt = ZonedDateTime.now();
        }
    }

    /**
     * restore 연산은 멱등하게 동작할 수 있도록 한다. (삭제되지 않은 엔티티를 복원해도 동일한 결과가 나오도록)
     */
    public void restore() {
        if (this.deletedAt != null) {
            this.deletedAt = null;
        }
    }
}
