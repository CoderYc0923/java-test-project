package com.sky.takeout.pojo.vo;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class EmployeeLoginVO {
    private Long id;
    private String username;
    private String name;
    private String token;
}
