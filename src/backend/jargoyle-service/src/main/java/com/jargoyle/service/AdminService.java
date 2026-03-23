package com.jargoyle.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.jargoyle.dto.AdminUserDto;
import com.jargoyle.dto.AdminUserUpdateRequest;
import com.jargoyle.entity.Role;
import com.jargoyle.entity.User;
import com.jargoyle.repository.UserRepository;
import com.jargoyle.service.exception.AdminOperationException;
import com.jargoyle.service.exception.AdminUserNotFoundException;

@Service
public class AdminService {

    private final UserRepository userRepository;

    public AdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Page<AdminUserDto> listUsers(Pageable pageable) {
        // Page.map() transforms each User entity into an AdminUserDto,
        // preserving all the pagination metadata (totalElements, etc.).
        return userRepository.findAll(pageable).map(this::toDto);
    }

    public AdminUserDto getUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new AdminUserNotFoundException(userId));
        return toDto(user);
    }

    public AdminUserDto updateUser(UUID actingAdminId, UUID targetId, AdminUserUpdateRequest request) {
        User target = userRepository.findById(targetId)
            .orElseThrow(() -> new AdminUserNotFoundException(targetId));

        // Role.valueOf() throws IllegalArgumentException for invalid values,
        // which GlobalExceptionHandler maps to 400 Bad Request.
        Role newRole = Role.valueOf(request.role());
        boolean newEnabled = request.enabled() != null ? request.enabled() : target.isEnabled();

        // Prevent demoting the last admin — the system must always have at least one.
        if (target.getRole() == Role.ADMIN && newRole != Role.ADMIN) {
            if (userRepository.countByRole(Role.ADMIN) == 1) {
                throw new AdminOperationException("Cannot demote the last admin user");
            }
        }

        // Re-check the enabled admin count on every update so disabling an admin
        // takes effect immediately for existing sessions without leaving the
        // system with no enabled administrator who can reverse the change.
        if (isDisablingOrDemotingEnabledAdmin(target, newRole, newEnabled)) {
            if (userRepository.countByRoleAndEnabledTrue(Role.ADMIN) == 1) {
                throw new AdminOperationException("Cannot disable the last enabled admin user");
            }
        }

        target.setRole(newRole);
        target.setEnabled(newEnabled);

        // Apply optional profile fields — null means "don't change".
        // Reject blank display names so users always have a visible label.
        if (request.displayName() != null) {
            if (request.displayName().isBlank()) {
                throw new IllegalArgumentException("Display name must not be blank");
            }
            target.setDisplayName(request.displayName());
        }
        if (request.email() != null) {
            target.setEmail(request.email());
        }

        userRepository.save(target);
        return toDto(target);
    }

    public void deleteUser(UUID actingAdminId, UUID targetId) {
        if (actingAdminId.equals(targetId)) {
            throw new AdminOperationException("Cannot delete your own account");
        }

        User target = userRepository.findById(targetId)
            .orElseThrow(() -> new AdminUserNotFoundException(targetId));

        // Prevent deleting the last admin.
        if (target.getRole() == Role.ADMIN && userRepository.countByRole(Role.ADMIN) == 1) {
            throw new AdminOperationException("Cannot delete the last admin user");
        }

        if (target.getRole() == Role.ADMIN && target.isEnabled() && userRepository.countByRoleAndEnabledTrue(Role.ADMIN) == 1) {
            throw new AdminOperationException("Cannot delete the last enabled admin user");
        }

        userRepository.deleteById(targetId);
    }

    private boolean isDisablingOrDemotingEnabledAdmin(User target, Role newRole, boolean newEnabled) {
        return target.getRole() == Role.ADMIN
            && target.isEnabled()
            && (newRole != Role.ADMIN || !newEnabled);
    }

    private AdminUserDto toDto(User user) {
        return new AdminUserDto(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getOauthProvider(),
            user.getRole().name(),
            user.isEnabled(),
            user.getCreatedAt(),
            user.getLastLoginAt()
        );
    }
}
