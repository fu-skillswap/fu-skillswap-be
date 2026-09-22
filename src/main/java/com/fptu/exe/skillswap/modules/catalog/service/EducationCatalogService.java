package com.fptu.exe.skillswap.modules.catalog.service;

import com.fptu.exe.skillswap.modules.catalog.domain.*;
import com.fptu.exe.skillswap.modules.catalog.dto.*;
import com.fptu.exe.skillswap.modules.catalog.repository.*;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class EducationCatalogService {
    private final AdministrativeProvinceRepository provinces;
    private final EducationalInstitutionRepository institutions;
    private final EducationFieldGroupRepository fieldGroups;

    public List<ProvinceResponse> provinces(String q) {
        String needle=normalize(q);
        return provinces.searchActive(needle).stream()
                .map(p -> new ProvinceResponse(p.getId(),p.getCode(),p.getName(),p.getUnitType())).toList();
    }
    public PageResponse<InstitutionResponse> institutions(String q, UUID provinceId, int page, int size) {
        String needle=normalize(q);
        Page<EducationalInstitution> result=institutions.searchActive(needle,provinceId,pageRequest(page,size));
        return page(result.map(i -> new InstitutionResponse(i.getId(),i.getSlug(),i.getName(),i.getShortName(),i.getProvince().getId(),i.getProvince().getName(),i.getInstitutionType())));
    }
    public PageResponse<EducationFieldGroupResponse> fieldGroups(String q, int page, int size) {
        String needle=normalize(q);
        Page<UUID> ids=fieldGroups.searchActiveIds(needle,pageRequest(page,size));
        if (ids.isEmpty()) return page(ids,List.of());
        Map<UUID,EducationFieldGroup> byId=fieldGroups.findWithAliasesByIdIn(ids.getContent()).stream().collect(java.util.stream.Collectors.toMap(EducationFieldGroup::getId,g->g));
        List<EducationFieldGroupResponse> content=ids.getContent().stream().map(byId::get)
                .map(g -> new EducationFieldGroupResponse(g.getId(),g.getOfficialCode(),g.getName(),List.copyOf(g.getAliases()))).toList();
        return page(ids,content);
    }
    private org.springframework.data.domain.Pageable pageRequest(int page,int size) {
        return org.springframework.data.domain.PageRequest.of(Math.max(0,page),Math.max(1,Math.min(size,100)));
    }
    private <T> PageResponse<T> page(Page<T> result) {
        return PageResponse.<T>builder().content(result.getContent()).page(result.getNumber()).size(result.getSize()).totalElements(result.getTotalElements()).totalPages(result.getTotalPages()).last(result.isLast()).build();
    }
    private <T> PageResponse<T> page(Page<?> metadata,List<T> content) {
        return PageResponse.<T>builder().content(content).page(metadata.getNumber()).size(metadata.getSize()).totalElements(metadata.getTotalElements()).totalPages(metadata.getTotalPages()).last(metadata.isLast()).build();
    }
    public static String normalize(String value) {
        if(value==null) return "";
        String decomposed=Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}+","").replace('đ','d');
        return decomposed.replaceAll("\\s+"," ");
    }
}
