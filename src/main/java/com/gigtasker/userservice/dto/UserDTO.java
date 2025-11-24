package com.gigtasker.userservice.dto;

import com.gigtasker.userservice.entity.Country;
import com.gigtasker.userservice.entity.Gender;
import com.gigtasker.userservice.entity.Role;
import com.gigtasker.userservice.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private List<String> roles;
    private UUID keycloakId;
    private Country country;
    private Gender gender;
    private LocalDate dateOfBirth;

    public static UserDTO fromEntity(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .keycloakId(user.getKeycloakId())
                .country(user.getCountry())
                .gender(user.getGender())
                .dateOfBirth(user.getDateOfBirth())
                .roles(user.getRoles().stream().map(r -> r.getName().name()).toList())
                .build();
    }
}
