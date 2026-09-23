# Gjj 前端代码模板（双栈）

> **第一步永远是判栈。** 打开目标工程 `package.json`：
> `vue@^2` + `element-ui` + `@vue/cli-service` → **A 栈**；`vue@^3` + `element-plus` + `vite` → **B 栈**。
> 两套写法**不得混用**。已有工程按现有栈写，不主动升级。

| 项 | A 栈（存量） | B 栈（新） |
|---|---|---|
| 代表工程 | `capinfo-gjj-frontend-jshs-gm`（prod/jiaxing/wenzhou/zaozhuang） | `capinfo-gjj-frontend-auth/*`、`jiaxing/capinfo-gjj-frontend-jshs` |
| 版本 | Vue 2.6 / Element UI 2.15.12 / vue-cli 4 / Vuex 3 / axios 0.18 | Vue 3.4 / Element Plus 2.7 / Vite 5 / Pinia 2 / axios 1.7 |
| 语言 | JavaScript（Options API） | TypeScript（`<script setup>`） |
| 页面落点 | `src/components/<业务域>/xxx.vue`（**无 views/**） | `src/views/<模块>/index.vue` |
| 接口落点 | `src/api/<业务域>/xxx.js` | `src/api/<模块>/xxx.ts` |
| 表格 | `x-table`（配置化 `config`） | `ProTable`（`:columns` + `:request-api`） |
| 复用逻辑 | `src/common/mixin.js` | `composables/useXxx.ts` |
| 包管理 | npm（未见 lock） | pnpm |

---

## A 栈（Vue 2）

### 接口层 `src/api/zjjs/xxx.js`

```js
import http from "@/common/http";

// 列表 URL 需 export，供 x-table 的 config.url 使用
export const GET_XXX_PAGE = "/api/v1/xxx/getXxxPage";
const SAVE_XXX = "/api/v1/xxx/addXxx";
const DELETE_XXX = "/api/v1/xxx/delXxx/";

export function saveXxx(data) {
  return http.post(SAVE_XXX, data);
}

export function deleteXxx(id) {
  return http.post(DELETE_XXX + id);
}
```

### 页面 `src/components/zjjs/xxx.vue`

```vue
<template>
  <div class="content-wrapper">
    <x-table ref="xtable" :config="config"></x-table>

    <x-dialog :title="textMap[dialogStatus]" :visible.sync="dialogFormVisible" width="70%" :modal-append-to-body="false">
      <el-form ref="dataForm" size="small" :rules="rules" :model="dataForm" label-width="120px">
        <el-row>
          <el-col :span="8">
            <el-form-item label="业务流水号" prop="ywlsh">
              <el-input v-model="dataForm.ywlsh" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="金额" prop="je">
              <money-input v-model="dataForm.je" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="dialogFormVisible = false">取消</el-button>
        <el-button v-if="dialogStatus === 'create'" type="primary" :loading="confirmLoading" @click="createData">确定</el-button>
        <el-button v-else type="primary" :loading="confirmLoading" @click="updateData">确定</el-button>
      </div>
    </x-dialog>
  </div>
</template>

<script>
import { saveXxx, deleteXxx, GET_XXX_PAGE } from "@/api/zjjs/xxx";
import XTable from "@/components/common/x-table.vue";
import XDialog from "@/components/common/x-dialog.vue";
import MoneyInput from "@/components/common/money-input.vue";

export default {
  name: "xxx",
  components: { XTable, XDialog, MoneyInput },
  data() {
    return {
      confirmLoading: false,
      dialogFormVisible: false,
      dialogStatus: "",
      textMap: { update: "编辑", create: "新增" },
      dataForm: { id: undefined, ywlsh: "", je: undefined },
      rules: {
        ywlsh: [{ required: true, message: "业务流水号不能为空", trigger: "blur" }]
      },
      config: {
        url: GET_XXX_PAGE,
        columns: [
          { prop: "ywlsh", label: "业务流水号" },
          { prop: "je", label: "金额" },
          { prop: "createdTime", label: "创建时间" }
        ],
        toolbar: [{ text: "新增", type: "primary", handle: this.handleCreate }],
        rowbar: {
          title: "操作",
          width: "200",
          buttons: [
            { text: "编辑", type: "primary", handle: this.handleUpdate },
            { text: "删除", type: "danger", handle: this.handleDelete }
          ]
        }
      }
    };
  },
  methods: {
    handleCreate() {
      this.dialogStatus = "create";
      this.dialogFormVisible = true;
      this.$nextTick(() => this.$refs["dataForm"].clearValidate());
    },
    handleUpdate(row) {
      this.dialogStatus = "update";
      this.dataForm = Object.assign({}, row);
      this.dialogFormVisible = true;
    },
    handleDelete(row) {
      this.$confirm("是否确定执行删除?", "提示", { type: "warning" }).then(() => {
        this.$refs.xtable.showLoading();
        deleteXxx(row.id).then(() => {
          this.$message({ type: "success", message: "操作成功!" });
          this.$refs.xtable.submitForm();
        });
      });
    },
    createData() {
      this.$refs["dataForm"].validate((valid) => {
        if (!valid) return;
        this.confirmLoading = true;
        saveXxx(this.dataForm).then(() => {
          this.$message({ type: "success", message: "操作成功!" });
          this.dialogFormVisible = false;
          this.$refs.xtable.submitForm();
        }).finally(() => { this.confirmLoading = false; });
      });
    },
    updateData() { /* 同 createData，调 updateXxx */ }
  }
};
</script>

<style scoped></style>
```

**A 栈要点**
- 查询/表格/分页三合一靠 `x-table` 的 `config`：`url` + `columns` + `toolbar` + `rowbar`；`columns[].search` 配 `type: 'dict' | 'select' | 'date'` 生成查询表单。
- 刷新列表：`this.$refs.xtable.submitForm()`；loading：`showLoading()`。
- 按钮权限：模板 `v-if="checkPermission('xxx:add')"`（来自 `src/common/permission.js`）。
- 公共组件在 `src/components/common/`：`x-table` `x-dialog` `cap-form` `money-input` `number-input` `org-tree-select` `center-select` `remote-select` `file-preview` `report-browser` 等，**优先复用**。

---

## B 栈（Vue 3 + TS）

### 接口层 `src/api/zjjs/xxx.ts`

```ts
import http from "@/common/http";
import { apiBase } from "@/common/apiBase";

const XXX_URL = `${apiBase.serverUrl}v1/xxx`;
export const LIST_XXX_PAGE = `${XXX_URL}/getXxxPage`;

export interface XxxQueryParams {
  current?: number;
  size?: number;
  ywlsh?: string;
}

export interface XxxRecord {
  id: string | number;
  ywlsh: string;
  je: number | string;
  createdTime?: string;
  [key: string]: any;
}

export interface XxxListResponse {
  records: XxxRecord[];
  total: number;
}

export const getXxxListApi = (params: XxxQueryParams) =>
  http.post<XxxListResponse>(LIST_XXX_PAGE, params);

export const createXxxApi = (params: Partial<XxxRecord>) =>
  http.post<void>(`${XXX_URL}/addXxx`, params);

export const deleteXxxApi = (id: string | number) =>
  http.post<void>(`${XXX_URL}/delXxx/${id}`);
```

### 组合式逻辑 `src/views/zjjs/xxx/composables/useXxxList.ts`

```ts
import { ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { ProTableInstance } from "@/components/ProTable/interface";
import { deleteXxxApi, getXxxListApi, type XxxQueryParams } from "@/api/zjjs/xxx";

export const useXxxList = () => {
  const proTableRef = ref<ProTableInstance>();
  const editVisible = ref(false);
  const dialogTitle = ref("");
  const currentRecord = ref<any>(null);

  const refreshTable = async () => { await proTableRef.value?.getTableList(); };

  const requestTableList = (params: XxxQueryParams) => getXxxListApi(params);

  const openCreateDialog = () => {
    currentRecord.value = null;
    dialogTitle.value = "新增";
    editVisible.value = true;
  };

  const openEditDialog = (row: any) => {
    currentRecord.value = { ...row };
    dialogTitle.value = "编辑";
    editVisible.value = true;
  };

  const handleDelete = async (row: any) => {
    await ElMessageBox.confirm(`确认删除「${row.ywlsh}」吗？`, "提示", { type: "warning" });
    await deleteXxxApi(row.id);
    ElMessage.success("删除成功");
    await refreshTable();
  };

  return { proTableRef, editVisible, dialogTitle, currentRecord, requestTableList, openCreateDialog, openEditDialog, handleDelete, refreshTable };
};
```

### 页面 `src/views/zjjs/xxx/index.vue`

```vue
<script setup lang="ts" name="XxxView">
import { computed, h } from "vue";
import { ElButton, ElMessageBox } from "element-plus";
import ProTable from "@/components/ProTable/index.vue";
import type { ColumnProps } from "@/components/ProTable/interface";
import EditDialog from "./components/EditDialog.vue";
import { useXxxList } from "./composables/useXxxList";
import type { XxxRecord } from "@/api/zjjs/xxx";

const { proTableRef, editVisible, dialogTitle, currentRecord, requestTableList, openCreateDialog, openEditDialog, handleDelete } = useXxxList();

const columns = computed<ColumnProps<XxxRecord>[]>(() => [
  { type: "index", label: "序号", width: 80 },
  { prop: "ywlsh", label: "业务流水号", minWidth: 160, search: { el: "input", props: { placeholder: "请输入业务流水号", clearable: true } } },
  { prop: "je", label: "金额", minWidth: 120 },
  { prop: "createdTime", label: "创建时间", minWidth: 180 },
  {
    prop: "operation", label: "操作", fixed: "right", width: 200,
    render: (scope) => h("div", { class: "xxx__actions" }, [
      h(ElButton, { type: "primary", link: true, onClick: () => openEditDialog(scope.row) }, () => "编辑"),
      h(ElButton, { type: "danger", link: true, onClick: () => handleDelete(scope.row) }, () => "删除")
    ])
  }
]);
</script>

<template>
  <div class="xxx table-box">
    <ProTable
      ref="proTableRef"
      title="Xxx管理"
      row-key="id"
      :columns="columns"
      :request-api="requestTableList"
      :pagination="true"
      :tool-button="['refresh', 'setting', 'search']"
    >
      <template #tableHeader>
        <el-button type="primary" @click="openCreateDialog">新增</el-button>
      </template>
    </ProTable>

    <EditDialog v-model="editVisible" :title="dialogTitle" :form-data="currentRecord" @submit="refreshTable" />
  </div>
</template>

<style scoped lang="scss">
@use "./index.scss" as *;
</style>
```

**B 栈要点**
- 页面 = `views/<模块>/index.vue`；逻辑抽 `composables/useXxx.ts`；弹窗 `components/XxxDialog.vue`。
- `ProTable` 的查询表单由 `columns[].search` 驱动；分页参数约定 `{ pageName: "current", sizeName: "size" }`，响应 `{ listName: "records", totalName: "total" }`。
- 组件文件名 PascalCase（`ProTable`、`EditDialog`），页面目录 kebab-case；`<script setup lang="ts" name="XxxView">`。
- 按钮权限用指令 `v-auth="'xxx'"`（无权限自动 `el.remove()`）或 `hooks/useAuthButtons.ts`。
- 公共组件在 `src/components/`：`ProTable` `SearchForm` `ImportExcel` `InfoDescriptions` `Common/Base/{MoneyInput,NumberInput,IdCardInput,BankcardInput}` `Common/Business/{OrgTreeSelect,CenterSelect,UserSelect,FileUpload,...}`。
- 官方规范文档可直接读：`prod/WebStromProject/capinfo-gjj-frontend-auth/capinfo-gjj-frontend-auth-manager/frontend_ai_rules.md`。

---

## 通用约定

- 金额输入统一用封装组件（A 栈 `money-input`，B 栈 `MoneyInput`），不手写 `el-input` + 正则。
- 字典下拉：A 栈走 `x-table` 的 `search.type === 'dict'` + `getDictList(typeCode, sysCode)`；B 栈走 `stores/modules/dict.ts`（key = `sysCode_typeCode`）。
- 导出：B 栈用 `hooks/useDownload.ts`（解析 `Content-Disposition`，兼容 `filename*=UTF-8''`）。
- 请求封装已统一解包（拦截器内 `data.data !== undefined ? data.data : data`），**业务层不要再 `.data.data`**。
- 成功 `code` 判定：`0` 或 `200`（后端 `R.getIsSuccess()` 同口径）。
