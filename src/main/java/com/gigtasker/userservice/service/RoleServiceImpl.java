package com.gigtasker.userservice.service;

import com.gigtasker.userservice.entity.Role;
import com.gigtasker.userservice.interfaces.RoleService;
import com.gigtasker.userservice.repository.RoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;

    private RoleService self; // proxy

    @Autowired
    @Lazy
    public void setSelf(RoleService self) {
        this.self = self;
    }

    public RoleServiceImpl(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Role> findRoleByName(String name) {
        return roleRepository.findByName(name);
    }

    @Override
    @Transactional
    public Role saveANewRole(String roleName) {
        return roleRepository.save(Role.builder().name(roleName).build());
    }

    @Override
    @Transactional
    public Role getRoleOrSave(String roleName) {
        return self.findRoleByName(roleName).orElseGet(() -> self.saveANewRole(roleName));
    }
}
