package com.gigtasker.userservice.controller;

import com.gigtasker.userservice.dto.UserDTO;
import com.gigtasker.userservice.dto.UserUpdateDTO;
import com.gigtasker.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserDTO> createUser(@RequestBody UserDTO userDTO) {
        UserDTO createdUser = userService.createUser(userDTO);
        return new ResponseEntity<>(createdUser, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
        UserDTO user = userService.getUserById(id);
        if (user != null) {
            return ResponseEntity.ok(user);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/me")
    public ResponseEntity<UserDTO> getMyProfile() {
        UserDTO user = userService.getMe();
        if (user != null) {
            return ResponseEntity.ok(user);
        } else {
            // This happens if they're logged into Keycloak, but
            // don't have a matching profile in our 'gig_users' table.
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/batch")
    public ResponseEntity<List<UserDTO>> getUsersByIds(@RequestBody List<Long> userIds) {
        return ResponseEntity.ok(userService.findUsersByIds(userIds));
    }

    @PostMapping("/{userId}/promote")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> promoteUser(@PathVariable Long userId) {
        userService.promoteUserToAdmin(userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        // We need to create findAll() in UserService
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PostMapping(value = "/{uuid}/avatar", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<UserDTO> uploadUserAvatar(@PathVariable UUID uuid,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(userService.updateProfileImage(uuid, file));
    }

    // SELF-SERVICE (My Profile)
    @PatchMapping("/me")
    public ResponseEntity<UserDTO> updateMyProfile(@RequestBody UserUpdateDTO updates,
            @AuthenticationPrincipal Jwt jwt) {
        UUID myId = UUID.fromString(jwt.getClaimAsString("sub"));
        return ResponseEntity.ok(userService.updateUser(myId, updates));
    }

    // ADMIN SERVICE (Update Any Profile)
    @PatchMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<UserDTO> updateUserProfile(@PathVariable UUID uuid,
            @RequestBody UserUpdateDTO updates) {
        return ResponseEntity.ok(userService.updateUser(uuid, updates));
    }
}
