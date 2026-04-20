---
name: "gjj-build-deploy"
description: "Handles Gjj compile/package only and local release aggregation. Invoke when user asks for 编译打包; if user asks 发布, switch to gjj-remote-deploy."
---

# Gjj Build and Deploy Skill

This skill automates pulling, building, packaging, and local release aggregation for Gjj regional projects (e.g., `zaozhuang`, `linyi`).

## Scope Boundary

- If user only says **编译打包**: execute compile/package and put artifacts into `<region-root>/release` only.
- If user says **发布**: do not perform remote upload/restart in this skill, switch to `gjj-remote-deploy`.

## Usage Guidelines

1. **Identify the Target Region:**
   The user will specify a regional directory (e.g., `/home/source/Jetbrains/Probject/Gjj/zaozhuang`). All operations should be anchored to this `<region-root>`.

2. **Backend Build Process:**
   - **Path:** `<region-root>/IdeaProjects/capinfo-gjj-busi-jshs`
   - **Toolchain:** `vfox` for `java@17.0.2+8` and `maven@3.9.14`.
   - **Commands:** 
     ```bash
     export JAVA_HOME="/home/source/.vfox/cache/java/v-17.0.2+8/java-17.0.2+8"
     export PATH="${JAVA_HOME}/bin:/home/source/.vfox/cache/maven/v-3.9.14/maven-3.9.14/bin:$PATH"
     git pull
     mvn clean package -DskipTests -s settings-tsp.xml
     ```
     *(Note: If Maven dependency issues occur regarding aliyun repositories, ensure `settings-tsp.xml` has the correct mirror configured.)*

3. **Frontend Build Process:**
   - **Path:** `<region-root>/WebstormProjects/capinfo-gjj-frontend-jshs-gm`
   - **Toolchain:** `vfox` for `nodejs@16.20.2`.
   - **Commands:**
     ```bash
     export PATH="/home/source/.vfox/cache/nodejs/v-16.20.2/nodejs-16.20.2/bin:$PATH"
     git pull
     rm -rf dist
     npm install
     npm run build
     zip -r capinfo-gjj-frontend-jshs-gm.zip dist
     ```

4. **Release Artifacts Aggregation (Local Only):**
   - Create a release directory at `<region-root>/release`.
   - Clear all existing files in `<region-root>/release` before aggregation.
   - Find all `*-app.jar` files in the backend project and copy them to the release directory.
   - Copy the frontend `capinfo-gjj-frontend-jshs-gm.zip` to the release directory.
   - **Commands:**
     ```bash
     mkdir -p <region-root>/release
     find <region-root>/release -mindepth 1 -delete
     find <region-root>/IdeaProjects/capinfo-gjj-busi-jshs -name "*-app.jar" -type f -exec cp {} <region-root>/release/ \;
     cp <region-root>/WebstormProjects/capinfo-gjj-frontend-jshs-gm/capinfo-gjj-frontend-jshs-gm.zip <region-root>/release/
     ```
   - If only frontend is being published, clear the old frontend package in `<region-root>/release` first, then copy the new zip:
     ```bash
     mkdir -p <region-root>/release
     find <region-root>/release -maxdepth 1 -type f -name "capinfo-gjj-frontend-jshs-gm.zip" -delete
     cp <region-root>/WebstormProjects/capinfo-gjj-frontend-jshs-gm/capinfo-gjj-frontend-jshs-gm.zip <region-root>/release/
     ```

## Examples

If the user says: *"更新枣庄的前后端项目，并编译打包，打包前先clear，然后统一放到发布目录"*, you should:
1. Locate the `<region-root>` as `zaozhuang`.
2. Execute the backend build steps.
3. Execute the frontend build steps.
4. Clear `zaozhuang/release` first, then aggregate the jars and frontend zip into this directory.

If the user says: *"把枣庄包发布到服务器"*:
1. Confirm artifacts are already in `<region-root>/release`.
2. Switch to `gjj-remote-deploy` for upload and K8s restart.
