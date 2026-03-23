package com.jargoyle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.jargoyle.dto.AdminUserUpdateRequest;
import com.jargoyle.entity.Role;
import com.jargoyle.entity.User;
import com.jargoyle.repository.UserRepository;
import com.jargoyle.service.exception.AdminOperationException;

/**
 * Unit tests for the admin user-management rules around role and enabled
 * status changes.
 */
class AdminServiceTests {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AdminService adminService = new AdminService(userRepository);

    /**
     * Verifies that admins can toggle a user's enabled flag through the update
     * request without needing a dedicated endpoint shape.
     */
    @Test
    void updateUser_updatesEnabledFlag() {
        var targetId = UUID.randomUUID();
        var user = createUser(targetId, Role.USER, false);

        when(userRepository.findById(targetId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        var result = adminService.updateUser(UUID.randomUUID(), targetId,
                new AdminUserUpdateRequest("USER", null, null, true));

        assertThat(user.isEnabled()).isTrue();
        assertThat(result.enabled()).isTrue();
        verify(userRepository).save(user);
    }

    /**
     * Prevents an admin from disabling the final enabled administrator, which
     * would otherwise leave nobody able to re-enable accounts.
     */
    @Test
    void updateUser_cannotDisableLastEnabledAdmin() {
        var targetId = UUID.randomUUID();
        var admin = createUser(targetId, Role.ADMIN, true);

        when(userRepository.findById(targetId)).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> adminService.updateUser(UUID.randomUUID(), targetId,
                new AdminUserUpdateRequest("ADMIN", null, null, false)))
            .isInstanceOf(AdminOperationException.class)
            .hasMessage("Cannot disable the last enabled admin user");

        verify(userRepository, never()).save(admin);
    }

    /**
     * Prevents deleting the final enabled admin even when other admin accounts
     * exist but are disabled.
     */
    @Test
    void deleteUser_cannotDeleteLastEnabledAdmin() {
        var targetId = UUID.randomUUID();
        var admin = createUser(targetId, Role.ADMIN, true);

        when(userRepository.findById(targetId)).thenReturn(Optional.of(admin));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(2L);
        when(userRepository.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> adminService.deleteUser(UUID.randomUUID(), targetId))
            .isInstanceOf(AdminOperationException.class)
            .hasMessage("Cannot delete the last enabled admin user");

        verify(userRepository, never()).deleteById(targetId);
    }

    private static User createUser(UUID id, Role role, boolean enabled) {
        var user = new User();
        user.setDisplayName("Test User");
        user.setEmail("test@example.com");
        user.setOauthProvider("google");
        user.setOauthSubject("subject-" + id);
        user.setRole(role);
        user.setEnabled(enabled);
        try {
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to set test user id", ex);
        }
        return user;
    }
}
