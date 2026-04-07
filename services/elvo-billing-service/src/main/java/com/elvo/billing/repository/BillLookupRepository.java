package com.elvo.billing.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elvo.billing.entity.BillLookup;

public interface BillLookupRepository extends JpaRepository<BillLookup, UUID>, BillLookupRepositoryCustom {
}