package com.fptu.exe.skillswap.shared.util;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class GenericSpecification<T> implements Specification<T> {
    private final SearchCriteria criteria;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        return switch (criteria.getOperator().toUpperCase()) {
            case "EQUALS" -> cb.equal(root.get(criteria.getKey()), criteria.getValue());
            case "LIKE" -> cb.like(cb.lower(root.get(criteria.getKey())),
                    "%" + criteria.getValue().toString().toLowerCase() + "%");
            case "GREATER_THAN" -> cb.greaterThan((Expression) root.get(criteria.getKey()), (Comparable) criteria.getValue());
            case "LESS_THAN" -> cb.lessThan((Expression) root.get(criteria.getKey()), (Comparable) criteria.getValue());
            default -> null;
        };
    }
}

