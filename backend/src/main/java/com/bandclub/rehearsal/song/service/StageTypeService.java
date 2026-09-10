package com.bandclub.rehearsal.song.service;

import com.bandclub.rehearsal.common.exception.AppException;
import com.bandclub.rehearsal.song.domain.StageType;
import com.bandclub.rehearsal.song.repository.StageTypeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.Locale;

@Service
public class StageTypeService {

    private final StageTypeRepository stageTypeRepository;
    private final Clock clock;

    public StageTypeService(StageTypeRepository stageTypeRepository, Clock clock) {
        this.stageTypeRepository = stageTypeRepository;
        this.clock = clock;
    }

    @Transactional
    public StageType resolveOrCreate(Long clubId, String input) {
        String displayName = normalizeDisplayName(input);
        String normalizedName = normalizeKey(displayName);
        return stageTypeRepository
                .findByClubIdAndNormalizedName(clubId, normalizedName)
                .orElseGet(() -> stageTypeRepository.save(StageType.create(
                        clubId, displayName, normalizedName, clock.instant()
                )));
    }

    @Transactional(readOnly = true)
    public StageType require(Long stageTypeId, Long clubId) {
        return stageTypeRepository.findByIdAndClubId(stageTypeId, clubId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "STAGE_TYPE_NOT_FOUND",
                        "무대 종류를 찾을 수 없습니다."
                ));
    }

    private String normalizeDisplayName(String input) {
        String value = input == null ? "" : input.trim().replaceAll("\\s+", " ");
        if (value.isBlank() || value.length() > 50) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_STAGE_TYPE_NAME",
                    "무대 종류는 1자 이상 50자 이하로 입력해주세요."
            );
        }
        return value;
    }

    private String normalizeKey(String displayName) {
        return displayName.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}