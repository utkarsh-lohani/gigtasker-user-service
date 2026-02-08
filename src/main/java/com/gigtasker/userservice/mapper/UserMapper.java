package com.gigtasker.userservice.mapper;

import com.gigtasker.common.dto.UserDTO;
import com.gigtasker.userservice.entity.Role;
import com.gigtasker.userservice.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    @Mapping(target = "gender", source = "gender.description")
    @Mapping(target = "country", source = "country.name")
    @Mapping(target = "roles", source = "roles", qualifiedByName = "mapRoleDescriptions")
    UserDTO toDTO(User user);

    @Named("mapRoleDescriptions")
    static List<String> mapRoleDescriptions(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return roles.stream()
                .map(r -> r.getName().name())
                .toList();
    }
}
