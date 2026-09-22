package com.fptu.exe.skillswap.modules.catalog.repository;
import com.fptu.exe.skillswap.modules.catalog.domain.EducationFieldGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface EducationFieldGroupRepository extends JpaRepository<EducationFieldGroup, UUID> {
    Optional<EducationFieldGroup> findByIdAndActiveTrue(UUID id);
    Optional<EducationFieldGroup> findByOfficialCode(String officialCode);
    @Query(value = "select g.id from EducationFieldGroup g where g.active = true and (:q = '' or g.normalizedName like concat('%', :q, '%') or exists (select a from EducationFieldGroup x join x.aliases a where x.id = g.id and function('translate', lower(a), 'áàảãạăắằẳẵặâấầẩẫậđèéẻẽẹêếềểễệìíỉĩịòóỏõọôốồổỗộơớờởỡợùúủũụưứừửữựỳýỷỹỵ', 'aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiiooooooooooooooooouuuuuuuuuuuyyyyy') like concat('%', :q, '%'))) order by g.sortOrder, g.id",
           countQuery = "select count(g) from EducationFieldGroup g where g.active = true and (:q = '' or g.normalizedName like concat('%', :q, '%') or exists (select a from EducationFieldGroup x join x.aliases a where x.id = g.id and function('translate', lower(a), 'áàảãạăắằẳẵặâấầẩẫậđèéẻẽẹêếềểễệìíỉĩịòóỏõọôốồổỗộơớờởỡợùúủũụưứừửữựỳýỷỹỵ', 'aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiiooooooooooooooooouuuuuuuuuuuyyyyy') like concat('%', :q, '%')))")
    Page<UUID> searchActiveIds(@Param("q") String normalizedQuery, Pageable pageable);
    @Query("select distinct g from EducationFieldGroup g left join fetch g.aliases where g.id in :ids")
    java.util.List<EducationFieldGroup> findWithAliasesByIdIn(@Param("ids") java.util.Collection<UUID> ids);
}
