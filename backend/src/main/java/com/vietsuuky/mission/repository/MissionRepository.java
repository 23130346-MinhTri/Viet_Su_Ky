package com.vietsuuky.mission.repository;

import com.vietsuuky.mission.entity.Mission;
import com.vietsuuky.mission.enums.MissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MissionRepository extends JpaRepository<Mission, UUID> {
    List<Mission> findByStatusOrderByMissionOrderAsc(MissionStatus status);
    Optional<Mission> findByCode(String code);
    List<Mission> findAllByOrderByMissionOrderAsc();
}
