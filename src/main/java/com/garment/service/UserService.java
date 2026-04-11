package com.garment.service;

import com.garment.dto.RoleAssignRequest;
import com.garment.dto.UserCreateRequest;
import com.garment.dto.UserUpdateRequest;
import com.garment.dto.UserVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    Page<UserVO> getUserList(String keyword, Pageable pageable);

    UserVO getUserById(String id);

    UserVO createUser(UserCreateRequest request);

    UserVO updateUser(String id, UserUpdateRequest request);

    void deleteUser(String id);

    UserVO assignRoles(String id, RoleAssignRequest request);

    UserVO updateUserStatus(String id, Integer status);

    UserVO getCurrentUser(String userId);
}
