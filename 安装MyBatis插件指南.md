# 在 Cursor 中安装 MyBatis 插件指南

## 推荐插件

### 1. MyBatisX（推荐）
- **功能**：支持 mapper.xml 和 mapper.java 之间的快速跳转
- **特点**：提供代码提示、方法名自动补全等功能

### 2. Free MyBatis Plugin
- **功能**：同样支持 XML 和 Java 接口之间的跳转
- **特点**：轻量级，功能专一

## 安装步骤

### 方法一：通过扩展市场 UI 安装（推荐）

1. **打开扩展面板**
   - 按快捷键 `Ctrl + Shift + X`（Windows）
   - 或点击左侧边栏的扩展图标（四个方块组成的图标）

2. **搜索插件**
   - 在搜索框中输入以下任一关键词：
     - `MyBatisX`
     - `Free MyBatis`
     - `MyBatis`

3. **安装插件**
   - 找到对应的插件后，点击 **"Install"** 按钮
   - 等待安装完成

4. **重启 Cursor**
   - 安装完成后，建议重启 Cursor 以使插件生效
   - 或按 `Ctrl + Shift + P`，输入 `Reload Window` 重新加载窗口

### 方法二：通过命令面板安装

1. 按 `Ctrl + Shift + P` 打开命令面板
2. 输入 `Extensions: Install Extensions`
3. 在搜索框中输入 `MyBatis` 或 `Free MyBatis`
4. 选择插件并点击安装

## 使用方法

安装完成后，插件会自动识别您的 MyBatis 项目：

1. **从 Java 跳转到 XML**
   - 在 `UserMapper.java` 接口中，方法名前会出现一个小图标（通常是箭头或跳转图标）
   - 点击该图标即可跳转到对应的 `UserMapper.xml` 中的 SQL 语句

2. **从 XML 跳转到 Java**
   - 在 `UserMapper.xml` 文件中，SQL 语句的 `id` 属性前会出现图标
   - 点击该图标即可跳转到对应的 Java 接口方法

3. **代码提示**
   - 在 XML 中编写 SQL 时，插件会提供 Java 接口方法的自动补全
   - 在 Java 接口中，也会提供 XML 中已定义的方法提示

## 验证安装

安装成功后，您可以：
- 打开 `demo-service/src/main/java/com/demo/mapper/UserMapper.java`
- 查看方法名旁边是否有跳转图标
- 打开 `demo-service/src/main/resources/mapper/UserMapper.xml`
- 查看 SQL 语句的 `id` 属性旁边是否有跳转图标

## 注意事项

- 确保您的项目已正确配置 MyBatis
- 确保 `mapper.xml` 文件中的 `namespace` 属性与 Java 接口的完整类名一致
- 如果插件不工作，检查 Java 扩展是否已安装（如 Language Support for Java）

## 当前项目配置检查

您的项目配置看起来是正确的：
- ✅ `UserMapper.xml` 的 namespace 为 `com.demo.mapper.UserMapper`
- ✅ `UserMapper.java` 的包路径为 `com.demo.mapper`
- ✅ XML 文件位于 `resources/mapper/` 目录下

安装插件后即可正常使用跳转功能！




