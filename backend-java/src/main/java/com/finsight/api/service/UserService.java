package com.finsight.api.service;

import com.finsight.api.domain.User;
import com.finsight.api.exception.ApiException;
import com.finsight.api.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public User get(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ApiException.unauthorized("Session no longer valid. Please sign in again."));
    }
}
