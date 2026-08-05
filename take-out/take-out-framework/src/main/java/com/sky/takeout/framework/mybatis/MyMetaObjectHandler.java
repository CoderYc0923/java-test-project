package com.sky.takeout.framework.mybatis;

import java.time.LocalDateTime;

import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.sky.takeout.common.context.BaseContext;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler{

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long currentId = BaseContext.getCurrentId();

        // 字段名必须和Entity属性名一致（驼峰）
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);

        if (currentId != null) {
            this.strictInsertFill(metaObject, "createUser", Long.class, currentId);
            this.strictInsertFill(metaObject, "updateUser", Long.class, currentId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long currentId = BaseContext.getCurrentId();

        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);

        if (currentId != null) {
            this.strictUpdateFill(metaObject, "updateUser", Long.class, currentId);
        }
    }
}
