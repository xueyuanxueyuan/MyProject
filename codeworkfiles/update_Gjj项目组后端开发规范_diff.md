# Gjj项目组后端开发规范修改对比（AI提交）

## 1. 目标
在 `/home/source/Jetbrains/Probject/Gjj/doc/rules/Gjj项目组后端开发规范.md` 中增加关于数据库设计公共字段要求，以及后端实体类继承父类的要求。

## 2. 修改点及对比

### 修改点一：5. 数据访问规范 (增加 11 个公共字段与基类继承要求)
**原代码：**
```markdown
## 5. 数据访问规范
- 简单 CRUD 优先 MyBatis-Plus（`LambdaQueryWrapper`）。
- 复杂联表、聚合、分页查询放 Mapper XML。
- XML 要求：
  - `namespace` 与 Mapper 接口一致；
  - 公共列定义可复用；
  - 动态 SQL 使用 `<where>/<if>/<foreach>`，避免字符串拼接。
- 逻辑删除字段与全局配置保持一致（常见为 `del_flag`）。
```

**新代码：**
```markdown
## 5. 数据访问规范
- **数据库表设计强制包含以下 11 个公共基础字段**：
  - `ID` (BIGINT, 主键, 必填)
  - `ZXBH` (NVARCHAR(15), 中心编号, 必填)
  - `REVISION` (INT, 乐观锁, 必填)
  - `CREATOR` (NVARCHAR(100), 创建人)
  - `CREATED_TIME` (TIMESTAMP(0), 创建时间, 必填)
  - `UPDATOR` (NVARCHAR(100), 更新人)
  - `UPDATED_TIME` (TIMESTAMP(0), 更新时间)
  - `JBJGBH` (NVARCHAR(30), 机构编号, 必填)
  - `JBJGMC` (NVARCHAR(90), 机构名称, 必填)
  - `QDBM` (NVARCHAR(16), 渠道编码)
  - `DEL_FLAG` (NVARCHAR(1), 删除标志, 必填)
- **基类继承与字段复用规范（避免重复字段）**：
  - 在编写代码时，所有的基类对象（如 DO/PO、Entity、DTO 等）**必须继承其对应的 `Base**` 父类**（如 `BaseDO`、`BaseEntity` 等）。
  - **严禁重复字段创建**：在声明子类属性前，必须检查父类是否已包含该公共字段（如上述的 11 个公共基础字段）。如果父类中已经定义了该属性（例如 `id`、`creator` 等），**绝对不要在子类中再次声明**，否则会导致 MyBatis 映射冲突、序列化异常等隐藏 bug。
- 简单 CRUD 优先 MyBatis-Plus（`LambdaQueryWrapper`）。
- 复杂联表、聚合、分页查询放 Mapper XML。
- XML 要求：
  - `namespace` 与 Mapper 接口一致；
  - 公共列定义可复用；
  - 动态 SQL 使用 `<where>/<if>/<foreach>`，避免字符串拼接。
- 逻辑删除字段与全局配置保持一致（即上述 `DEL_FLAG` 字段）。
```

## 3. 解决思路
1. **分析需求**：从用户提供的图片中提取出公积金项目表结构强制要求的11个字段（`ID`, `ZXBH`, `REVISION`, `CREATOR`, `CREATED_TIME`, `UPDATOR`, `UPDATED_TIME`, `JBJGBH`, `JBJGMC`, `QDBM`, `DEL_FLAG`）。
2. **规范整合**：这11个字段属于数据库设计的范畴，实体类（如 DO/Entity/DTO）的继承属于后端类定义规范，二者关系紧密，因此统一添加到《数据访问规范》章节中。
3. **补充强调**：在强调基类继承时，着重说明了**禁止重复字段创建**的规则，确保子类不会覆盖 `Base**` 父类中的公共字段，以避免 MyBatis 映射时发生类型冲突或赋值失败的隐患。
