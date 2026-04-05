package com.elvo.identity.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.identity.entity.Device;

@Repository
@Transactional(readOnly = true)
public interface DeviceRepository extends JpaRepository<Device, UUID> {

    List<Device> findByUserIdOrderByLastUsedAtDesc(UUID userId);

    List<Device> findByUserIdAndTrustedTrueAndRevokedFalseOrderByLastUsedAtDesc(UUID userId);

    List<Device> findByUserIdAndRevoked(UUID userId, boolean revoked);

    Optional<Device> findByDeviceId(String deviceId);
}
