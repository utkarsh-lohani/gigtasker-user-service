package com.gigtasker.userservice.interfaces;

import com.gigtasker.userservice.entity.Role;

import java.util.Optional;

public interface RoleService {
    Optional<Role> findRoleByName(String name);
    Role saveANewRole(String roleName);
    Role getRoleOrSave(String roleName);
}
