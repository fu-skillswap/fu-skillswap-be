package com.fptu.exe.skillswap.modules.catalog.repository;
import com.fptu.exe.skillswap.modules.catalog.domain.AdministrativeProvince;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface AdministrativeProvinceRepository extends JpaRepository<AdministrativeProvince, UUID> {
    Optional<AdministrativeProvince> findByIdAndActiveTrue(UUID id);
    Optional<AdministrativeProvince> findByCode(String code);
    @Query("select p from AdministrativeProvince p where p.active = true and (:q = '' or p.normalizedName like concat('%', :q, '%')) order by p.sortOrder, p.id")
    List<AdministrativeProvince> searchActive(@Param("q") String normalizedQuery);
}
