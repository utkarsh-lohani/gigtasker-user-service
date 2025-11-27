package com.gigtasker.userservice.entity;

import com.gigtasker.userservice.enums.GenderType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "genders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Gender {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(unique = true, nullable = false)
    private GenderType name;

    private String description;
}
