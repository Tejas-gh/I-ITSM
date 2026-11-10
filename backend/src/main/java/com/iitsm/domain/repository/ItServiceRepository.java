package com.iitsm.domain.repository;

import com.iitsm.domain.model.ItService;

import java.util.List;
import java.util.Optional;

public interface ItServiceRepository {

    ItService save(ItService s);

    Optional<ItService> findById(String id);

    List<ItService> findAll();
}
