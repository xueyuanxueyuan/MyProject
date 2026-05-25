# vfox 在 Trae agent 中环境变量问题解决方案

## 问题描述

在 Trae agent 中执行 `vfox use java@17.0.2+8 && vfox use maven@3.9.14 && java -version && mvn -v` 时，虽然 vfox 显示切换成功，但后续命令仍然找不到 `java` 和 `mvn`。

## 问题根源

vfox 通过 **shell 钩子** 修改环境变量（PATH、JAVA_HOME 等），这种修改依赖于 shell 的初始化和交互模式。在 Trae agent 的某些非交互终端会话中，shell 钩子可能没有被正确应用，导致环境变量未实际更新。

## 解决方案

### 方案一：使用 eval 和 vfox env（推荐）

在命令链中显式加载 vfox 环境变量：

```bash
cd /path/to/project
vfox use java@17.0.2+8
vfox use maven@3.9.14
eval "$(vfox env -s bash)"  # 关键：显式加载环境变量
java -version
mvn -v
```

或者合并成一行：

```bash
vfox use java@17.0.2+8 && vfox use maven@3.9.14 && eval "$(vfox env -s bash)" && java -version && mvn -v
```

### 方案二：使用 vfox exec（备选）

```bash
vfox use java@17.0.2+8 && vfox use maven@3.9.14 && vfox exec -- sh -c "java -version && mvn -v"
```

## 验证步骤

1. 执行 vfox use 命令切换 SDK
2. 执行 `eval "$(vfox env -s bash)"` 加载环境变量
3. 验证版本：`java -version` 和 `mvn -v`

## 结论

在 Trae agent 中使用 vfox 时，务必在切换 SDK 后加上 `eval "$(vfox env -s bash)"`，以确保环境变量被正确加载。
