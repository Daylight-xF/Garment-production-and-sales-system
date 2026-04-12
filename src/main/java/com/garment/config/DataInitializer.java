package com.garment.config;

import com.garment.model.Role;
import com.garment.model.User;
import com.garment.repository.RoleRepository;
import com.garment.repository.UserRepository;
import com.garment.util.PermissionConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initData(RoleRepository roleRepository,
                                       UserRepository userRepository,
                                       PasswordEncoder passwordEncoder) {
        return args -> {
            initRoles(roleRepository);
            initAdminUser(userRepository, roleRepository, passwordEncoder);
        };
    }

    private void initRoles(RoleRepository roleRepository) {
        List<Role> rolesToSave = new ArrayList<>();

        Role adminRole = roleRepository.findByCode("admin").orElse(null);
        if (adminRole == null) {
            adminRole = new Role();
            adminRole.setName("系统管理员");
            adminRole.setCode("admin");
            adminRole.setDescription("拥有系统所有权限");
        }
        adminRole.setPermissions(Arrays.asList(PermissionConstants.ADMIN_PERMISSIONS));
        rolesToSave.add(adminRole);

        Role productionManagerRole = roleRepository.findByCode("production_manager").orElse(null);
        if (productionManagerRole == null) {
            productionManagerRole = new Role();
            productionManagerRole.setName("生产管理人员");
            productionManagerRole.setCode("production_manager");
            productionManagerRole.setDescription("负责生产计划与任务管理");
        }
        productionManagerRole.setPermissions(Arrays.asList(PermissionConstants.PRODUCTION_MANAGER_PERMISSIONS));
        rolesToSave.add(productionManagerRole);

        Role warehouseStaffRole = roleRepository.findByCode("warehouse_staff").orElse(null);
        if (warehouseStaffRole == null) {
            warehouseStaffRole = new Role();
            warehouseStaffRole.setName("仓库操作人员");
            warehouseStaffRole.setCode("warehouse_staff");
            warehouseStaffRole.setDescription("负责库存管理与出入库操作");
        }
        warehouseStaffRole.setPermissions(Arrays.asList(PermissionConstants.WAREHOUSE_STAFF_PERMISSIONS));
        rolesToSave.add(warehouseStaffRole);

        Role salesStaffRole = roleRepository.findByCode("sales_staff").orElse(null);
        if (salesStaffRole == null) {
            salesStaffRole = new Role();
            salesStaffRole.setName("销售人员");
            salesStaffRole.setCode("sales_staff");
            salesStaffRole.setDescription("负责订单与销售管理");
        }
        salesStaffRole.setPermissions(Arrays.asList(PermissionConstants.SALES_STAFF_PERMISSIONS));
        rolesToSave.add(salesStaffRole);

        Role inactiveRole = roleRepository.findByCode("inactive").orElse(null);
        if (inactiveRole == null) {
            inactiveRole = new Role();
            inactiveRole.setName("未激活用户");
            inactiveRole.setCode("inactive");
            inactiveRole.setDescription("新注册用户，等待管理员激活");
        }
        inactiveRole.setPermissions(Arrays.asList(PermissionConstants.INACTIVE_PERMISSIONS));
        rolesToSave.add(inactiveRole);

        if (!rolesToSave.isEmpty()) {
            roleRepository.saveAll(rolesToSave);
            log.info("角色数据初始化/更新完成，共保存 {} 个角色", rolesToSave.size());
        }
    }

    private void initAdminUser(UserRepository userRepository,
                                RoleRepository roleRepository,
                                PasswordEncoder passwordEncoder) {
        if (userRepository.findByUsername("admin").isPresent()) {
            log.info("管理员账号已存在，跳过初始化");
            return;
        }

        Role adminRole = roleRepository.findByCode("admin")
                .orElseThrow(() -> new IllegalStateException("管理员角色未找到，请检查角色初始化"));

        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setRealName("系统管理员");
        admin.setStatus(1);
        admin.setRoles(Collections.singletonList(adminRole.getId()));

        userRepository.save(admin);
        log.info("管理员账号初始化完成");
    }
}
