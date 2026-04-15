package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CUserAuthMapper {
    User getByUsername(String username);

    void insert(User user);

    User getByUsernameAndPassword(@Param("username") String username, @Param("password") String password);
}
