package com.fptu.exe.skillswap.modules.catalog.repository;
import com.fptu.exe.skillswap.modules.catalog.domain.EducationalInstitution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface EducationalInstitutionRepository extends JpaRepository<EducationalInstitution, UUID> {
    @EntityGraph(attributePaths = "province")
    Optional<EducationalInstitution> findByIdAndActiveTrue(UUID id);
    Optional<EducationalInstitution> findBySlug(String slug);
    @EntityGraph(attributePaths = "province")
    @Query(value = "select i from EducationalInstitution i where i.active = true and (:provinceId is null or i.province.id = :provinceId) and (:q = '' or i.normalizedName like concat('%', :q, '%') or function('translate', lower(i.name), 'áàảãạăắằẳẵặâấầẩẫậđèéẻẽẹêếềểễệìíỉĩịòóỏõọôốồổỗộơớờởỡợùúủũụưứừửữựỳýỷỹỵ', 'aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiiooooooooooooooooouuuuuuuuuuuyyyyy') like concat('%', :q, '%') or function('translate', lower(i.shortName), 'áàảãạăắằẳẵặâấầẩẫậđèéẻẽẹêếềểễệìíỉĩịòóỏõọôốồổỗộơớờởỡợùúủũụưứừửữựỳýỷỹỵ', 'aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiiooooooooooooooooouuuuuuuuuuuyyyyy') like concat('%', :q, '%')) order by i.sortOrder, i.id",
           countQuery = "select count(i) from EducationalInstitution i where i.active = true and (:provinceId is null or i.province.id = :provinceId) and (:q = '' or i.normalizedName like concat('%', :q, '%') or function('translate', lower(i.name), 'áàảãạăắằẳẵặâấầẩẫậđèéẻẽẹêếềểễệìíỉĩịòóỏõọôốồổỗộơớờởỡợùúủũụưứừửữựỳýỷỹỵ', 'aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiiooooooooooooooooouuuuuuuuuuuyyyyy') like concat('%', :q, '%') or function('translate', lower(i.shortName), 'áàảãạăắằẳẵặâấầẩẫậđèéẻẽẹêếềểễệìíỉĩịòóỏõọôốồổỗộơớờởỡợùúủũụưứừửữựỳýỷỹỵ', 'aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiiooooooooooooooooouuuuuuuuuuuyyyyy') like concat('%', :q, '%'))")
    Page<EducationalInstitution> searchActive(@Param("q") String normalizedQuery, @Param("provinceId") UUID provinceId, Pageable pageable);
}
