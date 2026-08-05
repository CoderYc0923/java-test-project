package com.sky.takeout.pojo.vo.employee;

import lombok.Data;
import com.sky.takeout.pojo.enums.Sex;
import java.time.LocalDateTime;

@Data
public class EmployeeVO {
    private Long id;
    private String name;
    private String username;
    private String phone;
    private Sex sex;
    private String idNumber;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createUser;
    private Long updateUser;
}
