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

/**
 * 系统基础数据初始化配置。
 *
 * <p>在 Spring Boot 应用启动时初始化内置角色、角色权限以及默认管理员账号。</p>
 */
@Slf4j
@Configuration
public class DataInitializer {


        /**
         * 初始化数据，在应用启动时自动执行
         * <p>
         * 该方法注册一个 CommandLineRunner，在应用启动完成后依次初始化系统角色和默认管理员账号。
         * </p>
         *
         * @param roleRepository 角色数据访问接口
         * @param userRepository 用户数据访问接口
         * @param passwordEncoder 密码加密器
         * @return CommandLineRunner实例，用于初始化角色和管理员用户
         */
        @Bean
        public CommandLineRunner initData(RoleRepository roleRepository,
                                           UserRepository userRepository,
                                           PasswordEncoder passwordEncoder) {
            // 返回启动执行器，交由 Spring Boot 在应用启动阶段调用。
            return args -> {
                // 先初始化角色和权限，确保后续管理员账号可以绑定管理员角色。
                initRoles(roleRepository);
                // 再初始化默认管理员账号；账号已存在时内部会自动跳过。
                initAdminUser(userRepository, roleRepository, passwordEncoder);
            };
        }


        /**
         * 初始化系统角色数据，包括管理员、生产经理、仓库人员、销售人员和未激活用户角色
         * <p>
         * 该方法以角色编码为唯一标识，已存在则更新权限和描述信息，不存在则创建新角色。
         * </p>
         *
         * @param roleRepository 角色数据访问接口
         */
        private void initRoles(RoleRepository roleRepository) {
            // 收集本次需要创建或更新的角色，最后统一批量保存。
            List<Role> rolesToSave = new ArrayList<>();

            // 初始化或更新系统管理员角色。
            Role adminRole = roleRepository.findByCode("admin").orElse(null);
            // 角色不存在时创建角色基础信息。
            if (adminRole == null) {
                adminRole = new Role();
                adminRole.setName("系统管理员");
                adminRole.setCode("admin");
                adminRole.setDescription("拥有系统所有权限");
            }
            // 设置管理员权限集合，并加入待保存列表。
            adminRole.setPermissions(Arrays.asList(PermissionConstants.ADMIN_PERMISSIONS));
            rolesToSave.add(adminRole);

            // 初始化或更新生产管理人员角色。
            Role productionManagerRole = roleRepository.findByCode("production_manager").orElse(null);
            // 角色不存在时创建角色基础信息。
            if (productionManagerRole == null) {
                productionManagerRole = new Role();
                productionManagerRole.setName("生产管理人员");
                productionManagerRole.setCode("production_manager");
                productionManagerRole.setDescription("负责生产计划与任务管理");
            }
            // 设置生产管理权限集合，并加入待保存列表。
            productionManagerRole.setPermissions(Arrays.asList(PermissionConstants.PRODUCTION_MANAGER_PERMISSIONS));
            rolesToSave.add(productionManagerRole);

            // 初始化或更新仓库操作人员角色。
            Role warehouseStaffRole = roleRepository.findByCode("warehouse_staff").orElse(null);
            // 角色不存在时创建角色基础信息。
            if (warehouseStaffRole == null) {
                warehouseStaffRole = new Role();
                warehouseStaffRole.setName("仓库操作人员");
                warehouseStaffRole.setCode("warehouse_staff");
                warehouseStaffRole.setDescription("负责库存管理与出入库操作");
            }
            // 设置仓库人员权限集合，并加入待保存列表。
            warehouseStaffRole.setPermissions(Arrays.asList(PermissionConstants.WAREHOUSE_STAFF_PERMISSIONS));
            rolesToSave.add(warehouseStaffRole);

            // 初始化或更新销售人员角色。
            Role salesStaffRole = roleRepository.findByCode("sales_staff").orElse(null);
            // 角色不存在时创建角色基础信息。
            if (salesStaffRole == null) {
                salesStaffRole = new Role();
                salesStaffRole.setName("销售人员");
                salesStaffRole.setCode("sales_staff");
                salesStaffRole.setDescription("负责订单与销售管理");
            }
            // 设置销售人员权限集合，并加入待保存列表。
            salesStaffRole.setPermissions(Arrays.asList(PermissionConstants.SALES_STAFF_PERMISSIONS));
            rolesToSave.add(salesStaffRole);

            // 初始化或更新未激活用户角色。
            Role inactiveRole = roleRepository.findByCode("inactive").orElse(null);
            // 角色不存在时创建角色基础信息。
            if (inactiveRole == null) {
                inactiveRole = new Role();
                inactiveRole.setName("未激活用户");
                inactiveRole.setCode("inactive");
                inactiveRole.setDescription("新注册用户，等待管理员激活");
            }
            // 设置未激活用户权限集合，并加入待保存列表。
            inactiveRole.setPermissions(Arrays.asList(PermissionConstants.INACTIVE_PERMISSIONS));
            rolesToSave.add(inactiveRole);

            // 有待保存角色时批量写入数据库，并输出初始化日志。
            if (!rolesToSave.isEmpty()) {
                roleRepository.saveAll(rolesToSave);
                log.info("角色数据初始化/更新完成，共保存 {} 个角色", rolesToSave.size());
            }
        }


        /**
         * 初始化管理员账号，如果已存在则跳过
         * <p>
         * 该方法创建默认 admin 账号并绑定管理员角色；如果 admin 用户已存在，则不会重复创建。
         * </p>
         *
         * @param userRepository 用户数据访问接口
         * @param roleRepository 角色数据访问接口
         * @param passwordEncoder 密码加密器
         */
        private void initAdminUser(UserRepository userRepository,
                                    RoleRepository roleRepository,
                                    PasswordEncoder passwordEncoder) {
            // 如果默认管理员账号已经存在，则跳过初始化，避免覆盖已有密码和角色。
            if (userRepository.findByUsername("admin").isPresent()) {
                log.info("管理员账号已存在，跳过初始化");
                return;
            }

            // 查询管理员角色，确保默认管理员账号可以绑定正确角色。
            Role adminRole = roleRepository.findByCode("admin")
                    .orElseThrow(() -> new IllegalStateException("管理员角色未找到，请检查角色初始化"));

            // 创建默认管理员用户对象并设置基础信息。
            User admin = new User();
            admin.setUsername("admin");
            // 使用 PasswordEncoder 加密默认密码后再保存。
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRealName("系统管理员");
            admin.setStatus(1);
            // 绑定管理员角色 ID。
            admin.setRoles(Collections.singletonList(adminRole.getId()));

            // 保存默认管理员账号并记录初始化日志。
            userRepository.save(admin);
            log.info("管理员账号初始化完成");
        }

    
}
