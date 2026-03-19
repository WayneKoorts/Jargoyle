package com.jargoyle.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jargoyle.dto.AdminUserDto;
import com.jargoyle.dto.AdminUserUpdateRequest;
import com.jargoyle.entity.User;
import com.jargoyle.service.AdminService;

import jakarta.validation.Valid;

/**
 * Admin-only endpoints. Access is restricted to users with the ADMIN role
 * via the URL-based rule in SecurityConfig ("/api/admin/**" → hasRole("ADMIN")).
 *
 * Spring auto-binds Pageable from query params: ?page=0&size=20&sort=displayName,asc
 * This is standard Spring Data Web support — no manual parsing needed.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, String>> dashboard() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/users")
    public Page<AdminUserDto> listUsers(Pageable pageable) {
        return adminService.listUsers(pageable);
    }

    @GetMapping("/users/{userId}")
    public AdminUserDto getUser(@PathVariable UUID userId) {
        return adminService.getUser(userId);
    }

    @PutMapping("/users/{userId}")
    public AdminUserDto updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminUserUpdateRequest request,
            @CurrentUser User actingAdmin) {
        return adminService.updateUser(actingAdmin.getId(), userId, request);
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable UUID userId,
            @CurrentUser User actingAdmin) {
        adminService.deleteUser(actingAdmin.getId(), userId);
        return ResponseEntity.noContent().build();
    }
}
