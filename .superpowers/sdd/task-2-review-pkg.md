# Task 2 review package (working tree, no commits)

## Stat
?? take-out-pojo/src/main/java/com/sky/takeout/pojo/entity/Employee.java
?? take-out-pojo/src/main/java/com/sky/takeout/pojo/vo/EmployeeVO.java

## Employee.java

package com.sky.takeout.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("employee")
public class Employee {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String username;
    private String password;
    private String phone;
    private String sex;
    private String idNumber;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createUser;
    private Long updateUser;
}

## EmployeeVO.java

package com.sky.takeout.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EmployeeVO {
    private Long id;
    private String name;
    private String username;
    private String phone;
    private String sex;
    private String idNumber;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createUser;
    private Long updateUser;
}
