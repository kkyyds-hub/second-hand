# MongoDB 6.0 - Project Setup

MongoDB 6.0 用于站内消息（order_messages），与 Spring Boot 2.7 兼容。

## 安装前准备

- 将 **zip** 放到：`C:\Users\kk\Downloads\mongodb-windows-x86_64-6.0.27.zip`  
  （从 https://www.mongodb.com/try/download/community 下载，选 6.0.x / Windows x64 / zip）

## 一键安装（推荐）

1. **右键 `run-mongodb-install.bat` → 以管理员身份运行**  
   - 从 `C:\Users\kk\Downloads\` 读取 zip，解压到 **D:\mongodb**  
   - 创建 data、log 目录，生成 **mongod.cfg**  
   - 若本机已安装 **mongosh**，会创建用户 **root / 密码 1234** 并开启认证  
   - 注册并启动 Windows 服务 **MongoDB**

2. **若未安装 mongosh**  
   - 脚本会以**无认证**方式配置，连接串：`mongodb://localhost:27017/demo`  
   - 需要密码时：先安装 [MongoDB Shell (mongosh)](https://www.mongodb.com/try/download/shell)，再在管理员 CMD 里执行一次：  
     `mongosh mongodb://localhost:27017/admin --eval "db.getSiblingDB('admin').createUser({user:'root',pwd:'1234',roles:['root']})"`  
   - 然后在 `D:\mongodb\mongod.cfg` 里在 `net:` 下增加两行：  
     `security:` 和 `  authorization: enabled`  
   - 重启服务：`net stop MongoDB` → `net start MongoDB`  
   - 项目 yml 使用：`mongodb://root:1234@localhost:27017/demo`

## 项目配置（已写好）

- **application-dev.yml**：`demo.mongodb.uri: mongodb://root:1234@localhost:27017/demo`  
- 数据库名 **demo**，集合 **order_messages** 会在首次发消息时自动创建。

## 安装后启动

- 服务方式：`net start MongoDB` 或在「服务」里启动 **MongoDB**  
- 或双击 **start-mongodb.bat** 以前台方式启动（不依赖服务）

## 版本

- **MongoDB 6.0.27**，兼容 Spring Boot 2.7 与 Spring Data MongoDB。
