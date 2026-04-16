package com.yangyag.todo.auth.service;

import com.yangyag.todo.auth.dto.CreateUserRequest;
import com.yangyag.todo.auth.dto.UserResponse;
import com.yangyag.todo.auth.entity.User;
import com.yangyag.todo.auth.exception.ConflictException;
import com.yangyag.todo.auth.exception.ResourceNotFoundException;
import com.yangyag.todo.auth.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserManagementService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserManagementService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new ConflictException("Login ID already exists");
        }

        User user = new User();
        user.setLoginId(request.loginId());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setName(request.name());
        user.setRole(request.resolvedRole());
        user.setActive(request.resolvedActive());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return userRepository.findAll(Sort.by(
                        Sort.Order.asc("createdAt"),
                        Sort.Order.asc("loginId")))
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public void deleteUser(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found");
        }
        userRepository.deleteById(userId);
    }
}
