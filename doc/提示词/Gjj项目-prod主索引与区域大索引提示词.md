# Gjj 项目 prod主索引与区域大索引提示词

## 1. 使用前提
- 当前工作区根目录：`/home/source/Jetbrains/Probject/Gjj`
- 区域定义：与 `prod` 同级目录即“区域落地目录”，例如 `zaozhuang`、`linyi`、`wenzhou`、`jiaxing`。
- 索引使用顺序：先命中 `prod`，再命中区域差异。

## 2. 区域目录大索引（同级目录口径）
- `prod`：通用产品主线代码（基线）。
- `zaozhuang`：枣庄区域落地代码。
- `linyi`：临沂区域落地代码。
- `wenzhou`：温州区域落地代码。
- `jiaxing`：嘉兴区域落地代码。

## 3. prod 主索引（基线）
### 3.1 后端主工程
- 路径：`prod/IdeaProjects/capinfo-gjj-busi-jshs`
- 聚合结构：
  - `capinfo-gjj-busi-zjjs-ywgl`
  - `capinfo-gjj-busi-zjjs-zhgl`
  - `capinfo-gjj-busi-zjjs-lcgl`
  - `capinfo-gjj-busi-zjjs-sp`
  - `capinfo-gjj-busi-cwhs-jzgl`
  - `capinfo-gjj-busi-jshs-gm-agg`
  - `capinfo-gjj-busi-jhgl-zjjh`
  - `capinfo-gjj-busi-zjjs-zbkh`
  - `capinfo-gjj-busi-zjjs-core`
- 分层约定：
  - `*-basic-svc-api` / `*-basic-svc-api-v2`：DTO、接口契约、Feign
  - `*-basic-svc-app`：启动与配置
  - `*-basic-svc-busi`：Controller、Service、Domain、DAO、Mapper/XML

### 3.2 prod 方法索引样本（优先命中）
- 文件：`.../zjjs/ywgl/.../ZjJycshDmServiceImpl.java`
  - `saveJycsh`
  - `updateJycsh`
  - `selectList`
  - `removeByJslsh`
  - `getJycsh`
  - `updateByJslsh`
- 文件：`.../zjjs/ywgl/.../YwglServiceImpl.java`
  - `getYwlxSfgxById`
  - `getYwlxSfgx`
  - `addYwlxSfgx`
  - `editYwlxSfgx`
  - `addWtdkjjzh`
  - `addCwzjdb`
  - `selectFkywList`
  - `selectSkywList`
  - `addCwzjzf`
  - `zhbdtzcl`
- 文件：`.../zjjs/ywgl/.../yht/service/impl/YhtServiceImpl.java`
  - `sign`
  - `cancel`
  - `submitSmsCode`
  - `queryProtocol`
  - `uploadProtocols`
  - `submitBatchTrade`
  - `submitRealtimeTrade`
  - `queryRealtimeTradeStatus`
  - `fetchReconDetail`
  - `receiveCallback`
- 文件：`.../cwhs/jzgl/.../CwhsServiceImpl.java`
  - `getZtszById`
  - `queryZtsz`
  - `addKmxx`
  - `editKmxx`
  - `pzhz`
  - `qxpzhz`
  - `getHzpzPage`

### 3.3 prod 数据访问索引样本（Mapper/XML）
- `.../zjjs/ywgl/.../mapper/ZjJycshDOMapper.xml`
- `.../zjjs/ywgl/.../mapper/CwzjzfDOMapper.xml`
- `.../zjjs/ywgl/.../mapper/FkywDOMapper.xml`
- `.../zjjs/ywgl/.../mapper/SkywDOMapper.xml`
- `.../cwhs/jzgl/.../mapper/PzDOMapper.xml`

## 4. 区域大索引（仅记录相对 prod 的差异）
### 4.1 zaozhuang（枣庄）
- 后端路径：`zaozhuang/IdeaProjects/capinfo-gjj-busi-jshs`
- 识别到的差异线索：
  - `report/raqsoft-zaozhuang` 目录存在（区域报表资产差异）
  - 一户通设计文档更细化（接口/报文/异常与补偿文档更全）

### 4.2 linyi（临沂）
- 后端路径：`linyi/IdeaProjects/capinfo-gjj-busi-jshs`
- 前端路径：`linyi/WebstormProjects/capinfo-gjj-frontend-jshs-gm`
- 识别到的差异线索：
  - `report/raqsoft/config` 存在 `raqsoftConfig-prod-linyi.xml`、`raqsoftConfig-test-linyi.xml`
  - 后端模块清单中未看到 `zjjs-zbkh`（按当前目录快照）

### 4.3 wenzhou（温州）
- 后端路径：`wenzhou/IdeaProjects/capinfo-gjj-busi-jshs`
- 前端路径：`wenzhou/WebstormProjects/capinfo-gjj-frontend-jshs-gm`
- 识别到的差异线索：
  - `report/raqsoft` 下存在温州相关报表命名，如 `...-wenzhou.rpx`
  - 后端模块清单中未看到 `zjjs-zbkh`（按当前目录快照）

### 4.4 jiaxing（嘉兴）
- 后端路径：`jiaxing/IdeaProjects/capinfo-gjj-busi-jshs`
- 前端路径：`jiaxing/WebstormProjects/capinfo-gjj-frontend-jshs-gm`
- 识别到的差异线索：
  - 多个模块显式存在 `api-v2` 子模块
  - `app` 层包含多数据库配置文件（dm/kingbase/mysql/postgre）

## 5. 执行指令模板（建议直接复用）
```markdown
请按以下索引执行：
1. 先在 `prod/IdeaProjects/capinfo-gjj-busi-jshs` 命中目标文件与方法；
2. 若涉及区域发布，再到 `<region>/IdeaProjects/capinfo-gjj-busi-jshs` 仅定位差异文件；
3. 输出必须包含：
   - prod 命中文件与方法
   - 区域差异文件与差异方法
   - 差异类型（配置/规则/接口字段/SQL口径）
   - 最小回归路径
```
