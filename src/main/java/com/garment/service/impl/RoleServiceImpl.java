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

    /**
     * 获取所有角色列表
     * <p>
     * 该方法查询并返回系统中所有的角色信息，不进行任何过滤或排序。
     * </p>
     *
     * @return 角色列表，包含系统中定义的所有角色
     */
    @Override
    public List<Role> getRoleList() {
        return roleRepository.findAll();
    }


    /**
     * 根据ID查询角色详情
     * <p>
     * 该方法从数据库中获取指定ID的角色信息。
     * 如果角色不存在，则抛出业务异常。
     * </p>
     *
     * @param id 角色的唯一标识符
     * @return 角色对象，包含角色的详细信息
     */
    @Override
    public Role getRoleById(String id) {
        // 从数据库查询角色，不存在时抛出异常
        return roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("角色不存在"));
    }


    /**
     * 根据角色代码查询角色信息
     * <p>
     * 该方法通过角色代码从数据库中获取对应的角色信息。
     * 如果角色不存在，则抛出业务异常。
     * </p>
     *
     * @param code 角色的唯一代码标识
     * @return 角色对象，包含角色的详细信息
     */
    @Override
    public Role getRoleByCode(String code) {
        // 从数据库查询角色，不存在时抛出异常
        return roleRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException("角色不存在"));
    }

}
