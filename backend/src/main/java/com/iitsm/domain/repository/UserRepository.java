package com.iitsm.domain.repository;

import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(String id);

    List<User> findAll();

    List<Technician> findAllTechnicians();
}
