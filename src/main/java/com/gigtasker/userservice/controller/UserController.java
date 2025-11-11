package com.gigtasker.userservice.controller;

import com.gigtasker.userservice.dto.UserDTO;
import com.gigtasker.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/hello")
    public String sayHello() {
        return "Hello from the User Service! ";
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> promoteUser(@PathVariable Long userId) {
        userService.promoteUserToAdmin(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        // We need to create findAll() in UserService
        return ResponseEntity.ok(userService.getAllUsers());
    }
}
