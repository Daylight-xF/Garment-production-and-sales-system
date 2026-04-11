package com.garment.service;

import com.garment.model.Role;

import java.util.List;

public interface RoleService {

    List<Role> getRoleList();

    Role getRoleById(String id);

    Role getRoleByCode(String code);
}
