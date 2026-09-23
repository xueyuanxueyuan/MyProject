# Gjj 后端代码模板

> 所有 import 路径均已在 `prod/IdeaProject` 实读核实（2026-09-10）。
> 占位说明：`Xxx` = 业务对象；`<域>/<子域>` 按目标模块替换（如 `zjjs/ywgl`）。
> 模板是**骨架**，不是复制粘贴即用的银弹：字段、校验、SQL、状态流转必须按需求填实，禁止留 `TODO`。

## 0. 落点速查

| 产物 | 模块 | 路径 |
|---|---|---|
| ReqDTO / RespDTO / EvtDTO | `-api` | `src/main/java/cn/capinfo/gjj/busi/<域>/<子域>/api/dto/{req,resp,evt}/` |
| 常量 | `-api` | `.../api/constant/XxxConstant.java` |
| Feign + 降级 | `-api` | `.../api/client/` |
| Controller | `-busi` | `.../busi/controller/XxxController.java` |
| Service | `-busi` | `.../busi/service/XxxService.java` + `impl/XxxServiceImpl.java` |
| 领域服务 | `-busi` | `.../busi/domain/service/XxxDmService.java` + `impl/XxxDmServiceImpl.java` |
| DO | `-busi` | `.../busi/domain/dao/XxxDO.java` |
| Mapper | `-busi` | `.../busi/domain/dao/mapper/XxxDOMapper.java` + 同目录 `XxxDOMapper.xml` |
| 领域实体（可选） | `-busi` | `.../busi/domain/entity/Xxx.java`（无后缀） |

## 1. Controller

```java
package cn.capinfo.gjj.busi.zjjs.ywgl.busi.controller;

import cn.capinfo.gjj.basic.auth.anno.Current;
import cn.capinfo.gjj.basic.auth.entity.InnerUser;
import cn.capinfo.gjj.basic.validate.anno.Check;
import cn.capinfo.gjj.busi.zjjs.ywgl.api.dto.req.XxxAddReqDTO;
import cn.capinfo.gjj.busi.zjjs.ywgl.api.dto.req.XxxPageReqDTO;
import cn.capinfo.gjj.busi.zjjs.ywgl.api.dto.resp.XxxRespDTO;
import cn.capinfo.gjj.busi.zjjs.ywgl.busi.service.XxxService;
import cn.capinfo.gjj.core.common.base.BaseController;
import cn.capinfo.gjj.core.common.base.R;
import cn.capinfo.gjj.core.common.user.CurrentUser;
import cn.capinfo.gjj.expand.operate.log.annotation.OptLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Xxx管理
 */
@RestController
@RequestMapping("/api/v1/xxx")
@Tag(name = "Xxx管理", description = "Xxx管理服务")
@Slf4j
public class XxxController extends BaseController {

    @Resource
    private XxxService xxxService;

    @OptLog("分页查询Xxx")
    @PostMapping("/getXxxPage")
    @Operation(summary = "分页查询Xxx", description = "按条件分页查询Xxx")
    public R<Page<XxxRespDTO>> getXxxPage(@RequestBody @Check XxxPageReqDTO req,
                                          @Current CurrentUser<InnerUser> currentUser) {
        InnerUser user = currentUser.getUserDetail();
        req.setZxbh(user.getZxbh());
        return xxxService.getXxxPage(req, currentUser);
    }

    @OptLog("新增Xxx")
    @PostMapping("/addXxx")
    @Operation(summary = "新增Xxx")
    public R<Boolean> addXxx(@RequestBody @Check XxxAddReqDTO req,
                             @Current CurrentUser<InnerUser> currentUser) {
        return xxxService.addXxx(req, currentUser);
    }
}
```

**要点**
- 必须 `extends BaseController`；类注解 `@RestController` + `@RequestMapping("/api/v1/...")` + `@Tag` + `@Slf4j`。
- 查询也用 `@PostMapping`（项目习惯），路径动词化 `get/add/edit/del/...`。
- 方法三件套：`@OptLog("中文描述")` + `@Operation(summary=...)` + `@Check`。
- 操作人：`@Current CurrentUser<InnerUser> currentUser`，需要中心编号时 `req.setZxbh(user.getZxbh())`。
- 只做参数装配与转发，**不写业务逻辑**。

## 2. Service（应用层）

```java
package cn.capinfo.gjj.busi.zjjs.ywgl.busi.service;

import cn.capinfo.gjj.busi.zjjs.ywgl.api.dto.req.XxxAddReqDTO;
import cn.capinfo.gjj.busi.zjjs.ywgl.api.dto.req.XxxPageReqDTO;
import cn.capinfo.gjj.busi.zjjs.ywgl.api.dto.resp.XxxRespDTO;
import cn.capinfo.gjj.core.common.base.R;
import cn.capinfo.gjj.core.common.user.CurrentUser;
import cn.capinfo.gjj.basic.auth.entity.InnerUser;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/** 接口保持裸接口：无注解、无继承。 */
public interface XxxService {

    R<Page<XxxRespDTO>> getXxxPage(XxxPageReqDTO req, CurrentUser<InnerUser> currentUser);

    R<Boolean> addXxx(XxxAddReqDTO req, CurrentUser<InnerUser> currentUser);
}
```

```java
package cn.capinfo.gjj.busi.zjjs.ywgl.busi.service.impl;

import cn.capinfo.gjj.basic.auth.entity.InnerUser;
import cn.capinfo.gjj.busi.zjjs.ywgl.api.dto.req.XxxAddReqDTO;
import cn.capinfo.gjj.busi.zjjs.ywgl.api.dto.req.XxxPageReqDTO;
import cn.capinfo.gjj.busi.zjjs.ywgl.api.dto.resp.XxxRespDTO;
import cn.capinfo.gjj.busi.zjjs.ywgl.busi.domain.dao.XxxDO;
import cn.capinfo.gjj.busi.zjjs.ywgl.busi.domain.service.XxxDmService;
import cn.capinfo.gjj.busi.zjjs.ywgl.busi.service.XxxService;
import cn.capinfo.gjj.core.common.base.R;
import cn.capinfo.gjj.core.common.exception.BizException;
import cn.capinfo.gjj.core.common.join.JoinBeanUtil;
import cn.capinfo.gjj.core.common.user.CurrentUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class XxxServiceImpl implements XxxService {

    @Resource
    private XxxDmService xxxDmService;

    @Override
    public R<Page<XxxRespDTO>> getXxxPage(XxxPageReqDTO req, CurrentUser<InnerUser> currentUser) {
        Page<XxxDO> page = req.toPage(XxxDO.class);
        LambdaQueryWrapper<XxxDO> wrapper = new LambdaQueryWrapper<XxxDO>()
                .eq(req.getYwlsh() != null, XxxDO::getYwlsh, req.getYwlsh())
                .orderByDesc(XxxDO::getCreatedTime);
        Page<XxxDO> result = xxxDmService.page(page, wrapper);
        Page<XxxRespDTO> resp = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        resp.setRecords(JoinBeanUtil.copyList(result.getRecords(), XxxRespDTO.class));
        return R.success(resp);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Boolean> addXxx(XxxAddReqDTO req, CurrentUser<InnerUser> currentUser) {
        XxxDO entity = JoinBeanUtil.copyBean(req, XxxDO.class);
        // 金额/日期/状态位：显式判空，禁止依赖默认 0 / null 隐式行为
        if (req.getJe() == null) {
            throw new BizException("金额不能为空");
        }
        boolean saved = xxxDmService.save(entity);
        if (!saved) {
            throw new BizException("新增Xxx失败");
        }
        log.info("新增Xxx成功, ywlsh={}", req.getYwlsh());
        return R.success(true);
    }
}
```

**要点**
- `@Service` + `@Slf4j`；`@Transactional(rollbackFor = Exception.class)` 写在 **Impl 方法**上，不在接口上。
- 失败用 `throw new BizException(...)`，不要返回 `R.fail()` 再让上层猜。
- Bean 转换 `JoinBeanUtil.copyBean(...)`（无 MapStruct）。
- 日志用 `log.info/error`，禁止 `System.out.println`。

## 3. 领域服务（Dm 层）

```java
package cn.capinfo.gjj.busi.zjjs.ywgl.busi.domain.service;

import cn.capinfo.gjj.basic.db.service.IBaseService;
import cn.capinfo.gjj.busi.zjjs.ywgl.busi.domain.dao.XxxDO;

public interface XxxDmService extends IBaseService<XxxDO> {
}
```

```java
package cn.capinfo.gjj.busi.zjjs.ywgl.busi.domain.service.impl;

import cn.capinfo.gjj.basic.db.service.impl.BaseServiceImpl;
import cn.capinfo.gjj.busi.zjjs.ywgl.busi.domain.dao.XxxDO;
import cn.capinfo.gjj.busi.zjjs.ywgl.busi.domain.dao.mapper.XxxDOMapper;
import cn.capinfo.gjj.busi.zjjs.ywgl.busi.domain.service.XxxDmService;
import org.springframework.stereotype.Service;

@Service
public class XxxDmServiceImpl extends BaseServiceImpl<XxxDOMapper, XxxDO> implements XxxDmService {
}
```

> 命名 `Dm` = Domain。继承后即获得 MP 通用 CRUD，自定义方法写在接口里、SQL 落 XML。

## 4. DO / Mapper / XML

```java
package cn.capinfo.gjj.busi.zjjs.ywgl.busi.domain.dao;

import cn.capinfo.gjj.basic.db.base.BaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Xxx 持久化对象（表 zjjs_xxx）
 */
@TableName("zjjs_xxx")
@Data
public class XxxDO extends BaseDO<XxxDO> {

    @TableField("ywlsh")
    private String ywlsh;

    @TableField("je")
    private BigDecimal je;

    @TableField("jstjsj")
    private Date jstjsj;
}
```

```java
package cn.capinfo.gjj.busi.zjjs.ywgl.busi.domain.dao.mapper;

import cn.capinfo.gjj.basic.db.mapper.IBaseMapper;
import cn.capinfo.gjj.busi.zjjs.ywgl.busi.domain.dao.XxxDO;

/**
 * Xxx Mapper接口
 */
public interface XxxDOMapper extends IBaseMapper<XxxDO> {
}
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="cn.capinfo.gjj.busi.zjjs.ywgl.busi.domain.dao.mapper.XxxDOMapper">

    <resultMap id="BaseResultMap" type="cn.capinfo.gjj.busi.zjjs.ywgl.busi.domain.dao.XxxDO">
        <id property="id" column="ID" jdbcType="BIGINT"/>
        <result property="ywlsh" column="YWLSH" jdbcType="VARCHAR"/>
        <result property="je" column="JE" jdbcType="DECIMAL"/>
        <result property="jstjsj" column="JSTJSJ" jdbcType="TIMESTAMP"/>
    </resultMap>

    <sql id="Base_Column_List">
        ID, YWLSH, JE, JSTJSJ, ZXBH, DEL_FLAG, CREATED_TIME
    </sql>

    <select id="selectByYwlsh" resultMap="BaseResultMap">
        SELECT <include refid="Base_Column_List"/>
        FROM ZJJS_XXX
        WHERE DEL_FLAG = '0'
          AND YWLSH = #{ywlsh}
    </select>
</mapper>
```

**要点**
- DO：`@TableName` + `@Data` + `extends BaseDO<XxxDO>`；**不要重复声明** `id/creator/createdTime/delFlag/revision`（BaseDO 已有）。
- Mapper：`extends IBaseMapper<XxxDO>`，**不加 `@Mapper`**（启动类已 `@MapperScan("cn.capinfo.gjj.**.mapper")`）。
- XML 放**与 Mapper 接口同目录**（非 `resources/mapper`）；SQL 中表名与字段名大写。
- 逻辑删除字段 `DEL_FLAG` 需手动带 `DEL_FLAG = '0'`（自定义 SQL 不会自动拼接）。

## 5. DTO

```java
package cn.capinfo.gjj.busi.zjjs.ywgl.api.dto.req;

import cn.capinfo.gjj.core.common.base.BasePage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class XxxPageReqDTO extends BasePage {

    @Schema(description = "业务流水号")
    @Size(max = 32, message = "[业务流水号]长度不能超过32")
    private String ywlsh;
}
```

```java
package cn.capinfo.gjj.busi.zjjs.ywgl.api.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class XxxAddReqDTO {

    @NotBlank(message = "[业务流水号]不能为空")
    @Schema(description = "业务流水号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ywlsh;

    @NotNull(message = "[金额]不能为空")
    @Schema(description = "金额", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal je;

    @Schema(description = "请求结算时间")
    private Date jstjsj;
}
```

```java
package cn.capinfo.gjj.busi.zjjs.ywgl.api.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class XxxRespDTO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "业务流水号")
    private String ywlsh;

    @Schema(description = "金额")
    private BigDecimal je;

    @Schema(description = "创建时间")
    private Date createdTime;
}
```

**要点**
- 分页 ReqDTO `extends BasePage`（`cn.capinfo.gjj.core.common.base.BasePage`），字段 `current/size`，方法 `toPage(Class)`。
- 校验：`jakarta.validation.constraints.*`（`NotBlank` / `NotNull` / `Size`）；长度也可见 `org.hibernate.validator.constraints.Length`。
- 文档：`io.swagger.v3.oas.annotations.media.Schema`（**不是** `@ApiModelProperty`）。
- EvtDTO 用于 MQ 消息体，落 `dto/evt/`。

## 6. Feign（含降级）

```java
package cn.capinfo.gjj.busi.zjjs.ywgl.api.client;

import cn.capinfo.gjj.busi.zjjs.ywgl.api.dto.req.XxxPageReqDTO;
import cn.capinfo.gjj.busi.zjjs.ywgl.api.dto.resp.XxxRespDTO;
import cn.capinfo.gjj.core.common.base.R;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "capinfo-gjj-busi-zjjs-ywgl-basic-svc",
        path = "/api/v1/xxx",
        url = "${gjj.capinfo-gjj-busi-zjjs-ywgl-basic-svc:}",
        fallback = XxxFeignClientFallback.class,
        fallbackFactory = XxxFallbackFactory.class)
public interface XxxFeignClient {

    @PostMapping("/getXxxPage")
    R<Page<XxxRespDTO>> getXxxPage(@RequestBody XxxPageReqDTO req);
}
```

```java
package cn.capinfo.gjj.busi.zjjs.ywgl.api.client;

import cn.capinfo.gjj.busi.zjjs.ywgl.api.dto.req.XxxPageReqDTO;
import cn.capinfo.gjj.busi.zjjs.ywgl.api.dto.resp.XxxRespDTO;
import cn.capinfo.gjj.core.common.base.R;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class XxxFeignClientFallback implements XxxFeignClient {

    @Override
    public R<Page<XxxRespDTO>> getXxxPage(XxxPageReqDTO req) {
        log.error("Feign 降级：查询Xxx失败, req={}", req);
        // 禁止 return null：必须可观测并返回可判定的失败结果
        return R.fail("查询Xxx失败，服务暂不可用");
    }
}
```

```java
package cn.capinfo.gjj.busi.zjjs.ywgl.api.client;

import cn.capinfo.gjj.core.common.exception.BizException;
import feign.hystrix.FallbackFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class XxxFallbackFactory implements FallbackFactory<XxxFeignClient> {

    @Override
    public XxxFeignClient create(Throwable cause) {
        log.error("Feign 调用异常，进入降级", cause);
        return new XxxFeignClient() {
            @Override
            public R<Page<XxxRespDTO>> getXxxPage(XxxPageReqDTO req) {
                throw new BizException("查询Xxx失败：" + cause.getMessage());
            }
        };
    }
}
```

**要点**
- 必须配 `fallback` 或 `fallbackFactory`；**降级禁止 `return null`**，必须日志 + `R.fail(...)` 或抛 `BizException`。
- `@FeignClient` 若带 `fallback`，Spring Cloud OpenFeign 4.x 的熔断默认关闭，需确认 `spring.cloud.openfeign.circuitbreaker.enabled`；且 fallback 类必须在 `scanBasePackages` 覆盖范围内（见 `spring-bean-injection-triage`）。

## 7. 常量

```java
package cn.capinfo.gjj.busi.zjjs.ywgl.api.constant;

public class XxxConstant {

    /** Xxx-结果通知topic */
    public static final String CAPINFO_GJJ_ZJJS_XXX_TOPIC = "CAPINFO_GJJ_ZJJS_XXX_TOPIC";

    /** 状态-待处理 */
    public static final String STATUS_PENDING = "0";

    /** 状态-已处理 */
    public static final String STATUS_DONE = "1";
}
```

## 8. 建表脚本骨架（达梦）

```sql
-- 落 doc/数据库脚本/YYYYMMDD-xxx建表.sql
CREATE TABLE ZJJS_XXX (
    ID BIGINT NOT NULL,
    ZXBH NVARCHAR(15) NOT NULL,
    REVISION INT NOT NULL,
    CREATOR NVARCHAR(100),
    CREATED_TIME TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(),
    UPDATOR NVARCHAR(100),
    UPDATED_TIME TIMESTAMP(0),
    JBJGBH NVARCHAR(30) NOT NULL,
    JBJGMC NVARCHAR(90) NOT NULL,
    WDBH NVARCHAR(30),
    WDMC NVARCHAR(90),
    QDLX NVARCHAR(10),
    QDBM NVARCHAR(16),
    DEL_FLAG NVARCHAR(1) NOT NULL DEFAULT '0',
    HSJGBH NVARCHAR(10) NOT NULL DEFAULT '01',
    YWLSH NVARCHAR(32),
    JE DECIMAL(18,2),
    JSTJSJ TIMESTAMP(0),
    PRIMARY KEY (ID)
);
```
> 15 个系统字段不得改名删减；表名与字段名大写、不带引号。
