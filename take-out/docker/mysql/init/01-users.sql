-- 业务账号（与 MYSQLACCOUNT.md 一致）
-- 仅在数据卷首次初始化时执行
CREATE USER IF NOT EXISTS 'takeout_rw'@'%' IDENTIFIED BY 'TakeoutRw@123';
CREATE USER IF NOT EXISTS 'takeout_ro'@'%' IDENTIFIED BY 'TakeoutRo@123';

GRANT ALL PRIVILEGES ON take_out.* TO 'takeout_rw'@'%';
GRANT SELECT ON take_out.* TO 'takeout_ro'@'%';

FLUSH PRIVILEGES;
