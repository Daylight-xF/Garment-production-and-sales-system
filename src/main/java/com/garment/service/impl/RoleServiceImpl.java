package com.garment.service.impl;

import com.garment.exception.BusinessException;
import com.garment.model.Role;
import com.garment.repository.RoleRepository;
import com.garment.service.RoleService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;

    public RoleServiceImpl(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public List<Role> getRoleList() {
        return roleRepository.findAll();
    }

    @Override
    public Role getRoleById(String id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("角色不存在"));
    }

    @Override
    public Role getRoleByCode(String code) {
        return roleRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException("角色不存在"));
    }
}
