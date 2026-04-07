package com.elvo.billing.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.elvo.billing.entity.BillLookup;
import com.elvo.billing.entity.enums.BillCategory;
import com.elvo.billing.entity.enums.LookupStatus;

@SpringBootTest
@ActiveProfiles("test")
class BillLookupRepositoryTest {

    @Autowired
    private BillLookupRepository billLookupRepository;

    @Test
    void createAndLookupBillShouldPersistAndReturnRecord() {
        BillLookup lookup = new BillLookup();
        lookup.setRequestId("lookup-123");
        lookup.setBillCategory(BillCategory.WATER);
        lookup.setServiceCode("waterco");
        lookup.setReferenceNumber("REF-L-001");
        lookup.setCustomerPhone("255700000010");
        lookup.setMetadata("{\"provider\":\"waterco\", \"attempt\":1}");
        lookup.setLookupStatus(LookupStatus.SUCCESS);
        lookup.setCustomerName("Test Customer");
        lookup.setAmount(new BigDecimal("50.00"));
        lookup.setCurrency("TZS");
        lookup.setDescription("Water bill lookup");
        lookup.setBillItems("[]");
        lookup.setRawProviderReference("RAW-LOOKUP-001");

        BillLookup created = billLookupRepository.createLookup(lookup);

        assertThat(created.getLookupId()).isNotNull();
        assertThat(created.getLookupStatus()).isEqualTo(LookupStatus.SUCCESS);
        assertThat(created.getMetadata()).isEqualTo("{\"provider\":\"waterco\",\"attempt\":1}");

        assertThat(billLookupRepository.getLookupById(created.getLookupId()))
                .isPresent()
                .get()
                .extracting(BillLookup::getReferenceNumber, BillLookup::getLookupStatus)
                .containsExactly("REF-L-001", LookupStatus.SUCCESS);

        assertThat(billLookupRepository.getLookupByReference("REF-L-001"))
                .isPresent()
                .get()
                .extracting(BillLookup::getAmount, BillLookup::getCurrency)
                .containsExactly(new BigDecimal("50.00"), "TZS");
    }

    @Test
    void updateLookupStatusShouldLockAndUpdateRow() {
        BillLookup lookup = new BillLookup();
        lookup.setRequestId("lookup-456");
        lookup.setBillCategory(BillCategory.ELECTRICITY);
        lookup.setServiceCode("powerco");
        lookup.setReferenceNumber("REF-L-002");
        lookup.setMetadata("{\"provider\":\"powerco\", \"attempt\":2}");
        lookup.setLookupStatus(LookupStatus.FAILED);

        BillLookup created = billLookupRepository.createLookup(lookup);

        BillLookup updated = billLookupRepository.updateLookupStatus(created.getLookupId(), LookupStatus.NOT_FOUND);

        assertThat(updated.getLookupStatus()).isEqualTo(LookupStatus.NOT_FOUND);
        assertThat(billLookupRepository.getLookupById(created.getLookupId()))
                .isPresent()
                .get()
                .extracting(BillLookup::getLookupStatus)
                .isEqualTo(LookupStatus.NOT_FOUND);
    }

    @Test
    void createLookupShouldRejectMissingBillCategory() {
        BillLookup lookup = new BillLookup();
        lookup.setRequestId("lookup-missing-category");
        lookup.setServiceCode("lookupco");
        lookup.setReferenceNumber("REF-MISSING-CATEGORY");
        lookup.setMetadata("{}");
        lookup.setLookupStatus(LookupStatus.SUCCESS);

        assertThatThrownBy(() -> billLookupRepository.createLookup(lookup))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("billCategory must not be null");
    }
}