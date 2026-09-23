import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const output = path.dirname(fileURLToPath(import.meta.url));
const workspace = path.resolve(output, '../../..');
const root = path.join(workspace, 'prod/IdeaProject/capinfo-gjj-busi-zjfk');
const ignored = new Set(['.git', '.idea', 'target', '__pycache__', '.venv', 'venv', 'node_modules']);
const files = [];
async function walk(directory) {
  for (const entry of await fs.readdir(directory, { withFileTypes: true })) {
    if (ignored.has(entry.name)) continue;
    const absolute = path.join(directory, entry.name);
    if (entry.isDirectory()) await walk(absolute);
    else files.push(path.relative(root, absolute).replaceAll('\\', '/'));
  }
}
await walk(root);
files.sort();
const sources = new Map();
for (const filename of files) {
  if (/\.(java|py|xml|sql)$/.test(filename) && !filename.startsWith('settings-')) {
    sources.set(filename, await fs.readFile(path.join(root, filename), 'utf8'));
  }
}
const nodes = new Map();
const edges = new Map();
const evidence = (filename, source, offset = 0) => ({ file: filename, line: source.slice(0, offset).split('\n').length });
function node(id, type, label, attributes = {}) {
  if (!nodes.has(id)) nodes.set(id, { id, type, label, ...attributes });
  return id;
}
function edge(source, target, type, proof) {
  const key = `${source}|${type}|${target}`;
  if (!edges.has(key)) edges.set(key, { source, target, type, evidence: proof });
}
const project = node('project:zjfk', 'project', '资金风控 zjfk');
const javaNames = new Map();
const inventory = [];
for (const [filename, source] of sources) {
  const moduleName = filename.split('/')[0];
  const moduleId = node(`module:${moduleName}`, 'module', moduleName);
  edge(project, moduleId, 'contains', { file: filename, line: 1 });
  const fileId = node(`file:${filename}`, 'file', path.basename(filename), { path: filename });
  edge(moduleId, fileId, 'contains', { file: filename, line: 1 });
  inventory.push({ path: filename, lines: source.split('\n').length, sha256: crypto.createHash('sha256').update(source).digest('hex') });
  if (filename.endsWith('.java')) {
    const packageName = source.match(/^package\s+([\w.]+);/m)?.[1];
    if (packageName) javaNames.set(`${packageName}.${path.basename(filename, '.java')}`, fileId);
  }
}
const ddlPath = '数据库设计/zjfk_db.sql';
const ddl = sources.get(ddlPath);
const tables = [];
for (const match of ddl.matchAll(/^\s*CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?["`]?([\w.]+)/gim)) {
  const proof = evidence(ddlPath, ddl, match.index);
  const tableId = node(`table:${match[1]}`, 'table', match[1], { evidence: proof });
  tables.push({ name: match[1], ...proof, entities: [] });
  edge(`file:${ddlPath}`, tableId, 'defines_schema', proof);
}
const endpoints = [];
const controllers = [];
const mounts = new Map();
const mainPath = 'capinfo-gjj-busi-zjfk-model/app/main.py';
for (const match of sources.get(mainPath).matchAll(/app\.include_router\(\s*(\w+)\.router\s*,([\s\S]*?)\)/g)) {
  mounts.set(match[1], match[2].match(/prefix\s*=\s*"([^"]+)"/)?.[1] || '');
}
for (const [filename, source] of sources) {
  const fileId = `file:${filename}`;
  if (filename.endsWith('.java')) {
    for (const match of source.matchAll(/^import\s+(?:static\s+)?([\w.]+);/gm)) {
      const target = javaNames.get(match[1]) || javaNames.get(match[1].slice(0, match[1].lastIndexOf('.')));
      if (target && target !== fileId) edge(fileId, target, 'imports', evidence(filename, source, match.index));
    }
    for (const match of source.matchAll(/@TableName\((?:value\s*=\s*)?"([^"]+)"/g)) {
      const proof = evidence(filename, source, match.index);
      const tableId = node(`table:${match[1]}`, 'table', match[1], { evidence: proof });
      edge(fileId, tableId, 'maps_to', proof);
      tables.find(table => table.name === match[1])?.entities.push(proof);
    }
    if (filename.endsWith('Controller.java')) {
      const base = source.match(/@RequestMapping\("([^"]+)"\)/)?.[1] || '';
      const found = [...source.matchAll(/@(Get|Post|Put|Delete|Patch)Mapping\(\s*(?:(?:value|path)\s*=\s*)?"([^"]*)"/g)];
      controllers.push({ name: path.basename(filename, '.java'), base, count: found.length, file: filename });
      for (const match of found) {
        const proof = evidence(filename, source, match.index);
        const item = { language: 'Java', method: match[1].toUpperCase(), route: base + match[2], ...proof };
        endpoints.push(item);
        edge(fileId, node(`endpoint:java:${item.method}:${item.route}`, 'endpoint', `${item.method} ${item.route}`, { evidence: proof }), 'routes', proof);
      }
    }
  }
  if (filename.includes('/app/api/routes/') && filename.endsWith('.py')) {
    const prefix = mounts.get(path.basename(filename, '.py'));
    if (prefix !== undefined) {
      for (const match of source.matchAll(/^@router\.(get|post|put|delete|patch)\("([^"]*)"/gm)) {
        const proof = evidence(filename, source, match.index);
        const item = { language: 'Python', method: match[1].toUpperCase(), route: prefix + match[2], ...proof };
        endpoints.push(item);
        edge(fileId, node(`endpoint:python:${item.method}:${item.route}`, 'endpoint', `${item.method} ${item.route}`, { evidence: proof }), 'routes', proof);
      }
    }
  }
  if (filename.endsWith('.xml') && /<mapper\s/.test(source)) {
    for (const match of source.matchAll(/\b(?:FROM|JOIN|UPDATE|INTO)\s+[`"]?((?:zjfk_)[\w]+)/gi)) {
      const proof = evidence(filename, source, match.index);
      edge(fileId, node(`table:${match[1]}`, 'table', match[1], { evidence: proof }), 'sql_references', proof);
    }
  }
  if (filename.endsWith('pom.xml')) {
    for (const match of source.matchAll(/<dependency>\s*<groupId>[^<]+<\/groupId>\s*<artifactId>([^<]+)<\/artifactId>/g)) {
      const targetId = `module:${match[1]}`;
      if (nodes.has(targetId)) edge(`module:${filename.split('/')[0]}`, targetId, 'depends_on', evidence(filename, source, match.index));
    }
  }
}
const totals = {
  scannedFiles: files.length,
  indexedTextFiles: inventory.length,
  javaFiles: files.filter(filename => filename.endsWith('.java')).length,
  pythonFiles: files.filter(filename => filename.endsWith('.py')).length,
  javaControllers: controllers.length,
  javaEndpoints: endpoints.filter(item => item.language === 'Java').length,
  pythonEndpoints: endpoints.filter(item => item.language === 'Python').length,
  ddlTables: tables.length,
  mappedEntities: tables.reduce((sum, table) => sum + table.entities.length, 0),
  nodes: nodes.size,
  edges: edges.size
};
const graph = {
  schemaVersion: 'zjfk-static-graph/1.0',
  sourceRoot: 'prod/IdeaProject/capinfo-gjj-busi-zjfk',
  generatedAt: new Date().toISOString(),
  gitCommit: execFileSync('git', ['-C', root, 'rev-parse', 'HEAD'], { encoding: 'utf8' }).trim(),
  method: '本地静态声明抽取；非AST调用图，非运行时追踪；Java imports不等于调用；SQL references不区分读写；Python只抽取已挂载路由。',
  excluded: [...ignored, '.env 内容', 'settings-*.xml 内容', '配置值', '数据文件内容', '模型二进制内容'],
  totals, nodes: [...nodes.values()], edges: [...edges.values()], inventory
};
for (const relation of graph.edges) {
  if (!nodes.has(relation.source) || !nodes.has(relation.target)) throw new Error(`悬空边 ${JSON.stringify(relation)}`);
  const source = sources.get(relation.evidence.file);
  if (source === undefined || relation.evidence.line < 1 || relation.evidence.line > source.split('\n').length) throw new Error('无效证据');
}
const intro = '# zjfk 接口与数据索引\n\n自动生成的源码导航，不是在线接口探测结果。相对路径均基于源码根目录。\n\n';
const controllerRows = controllers.map(item => `| ${item.name} | \`${item.base}\` | ${item.count} |`).join('\n');
const endpointRows = endpoints.map(item => `| ${item.language} | ${item.method} | \`${item.route}\` | \`${item.file}:${item.line}\` |`).join('\n');
const tableRows = tables.map(table => `| \`${table.name}\` | ${table.line} | ${table.entities.map(item => `\`${item.file}:${item.line}\``).join('<br>') || '本次未检出直接 @TableName 映射'} |`).join('\n');
const index = `${intro}## 统计口径\n\n\`\`\`json\n${JSON.stringify(totals, null, 2)}\n\`\`\`\n\n## Java 控制器\n\n| 控制器 | 基础路径 | 方法数 |\n|---|---|---:|\n${controllerRows}\n\n## Java 与 Python 路由\n\n只统计显式方法映射；Python 路径已与 app/main.py 中挂载前缀拼接。排除框架自动生成的文档路由。\n\n| 服务 | HTTP | 完整路径 | 证据 |\n|---|---|---|---|\n${endpointRows}\n\n## 建表与实体\n\n建表依据 \`数据库设计/zjfk_db.sql\`，不代表真实数据库已部署；排除行注释中的建表文本；未映射不等于未使用。\n\n| 表名 | DDL 起始行 | 实体映射 |\n|---|---:|---|\n${tableRows}\n`;
await fs.writeFile(path.join(output, 'knowledge-graph.json'), JSON.stringify(graph, null, 2) + '\n', 'utf8');
await fs.writeFile(path.join(output, '接口与数据索引.md'), index, 'utf8');
await fs.writeFile(path.join(output, 'endpoints.json'), JSON.stringify(endpoints, null, 2) + '\n', 'utf8');
console.log(JSON.stringify({ validation: 'PASS', totals }, null, 2));
const overviewNodes = [
  ['app', 'APP 启动入口', 'Spring Boot / Java 17', 590, 110, '#e0e7ff'],
  ['api', 'API 共享契约', '请求与响应 DTO', 170, 270, '#e0e7ff'],
  ['busi', 'BUSI 业务服务', '24 个控制器', 590, 270, '#dbeafe'],
  ['python', 'Python 模型服务', 'FastAPI / 五类算法', 1120, 270, '#d1fae5'],
  ['pzgl', '配置管理', '数据源 / 连接测试', 30, 470, '#dbeafe'],
  ['mxgl', '模型管理', '数据集 / 训练 / 比较', 265, 470, '#dbeafe'],
  ['mxyy', '预测与应用', '预警 / 测算 / 计划', 500, 470, '#dbeafe'],
  ['doc', '资金报告', '模板 / 生成 / 下载', 735, 470, '#dbeafe'],
  ['job', '任务调度', '注册 / 触发 / 同步', 970, 470, '#dbeafe'],
  ['kubeflow', 'Kubeflow / KServe', '部分能力未完成', 1205, 470, '#fef3c7'],
  ['db', '业务库与采集来源', '逻辑分离 / 未连接验证', 145, 695, '#f1f5f9'],
  ['objects', '对象存储与文件', '数据集 / 报告资产', 610, 695, '#f1f5f9'],
  ['models', 'Python 持久化目录', '模型 / 任务 / 结果', 1075, 695, '#d1fae5']
];
const overviewEdges = [
  ['app', 'busi', false], ['busi', 'api', false], ['busi', 'python', false],
  ['busi', 'pzgl', false], ['busi', 'mxgl', false], ['busi', 'mxyy', false],
  ['busi', 'doc', false], ['busi', 'job', false], ['busi', 'kubeflow', true],
  ['pzgl', 'db', false], ['mxgl', 'db', false], ['doc', 'objects', false],
  ['mxgl', 'objects', false], ['python', 'models', false]
];
const escapeXml = value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
const overviewMap = new Map(overviewNodes.map(item => [item[0], item]));
const edgeSvg = overviewEdges.map(([sourceId, targetId, dashed]) => {
  const source = overviewMap.get(sourceId);
  const target = overviewMap.get(targetId);
  const horizontal = source[4] === target[4];
  const direction = target[3] > source[3] ? 1 : -1;
  const startX = horizontal ? source[3] + (direction > 0 ? 200 : 0) : source[3] + 100;
  const startY = source[4] + (horizontal ? 40 : 80);
  const endX = horizontal ? target[3] + (direction > 0 ? 0 : 200) : target[3] + 100;
  const endY = target[4] + (horizontal ? 40 : 0);
  return `<path d="M ${startX} ${startY} L ${endX} ${endY}" fill="none" stroke="${dashed ? '#d97706' : '#94a3b8'}" stroke-width="2" ${dashed ? 'stroke-dasharray="6 5"' : ''} marker-end="url(#arrow)"/>`;
}).join('\n');
const nodeSvg = overviewNodes.map(([, label, detail, horizontal, vertical, color]) => `<g><rect x="${horizontal}" y="${vertical}" width="200" height="80" rx="12" fill="${color}" stroke="#cbd5e1"/><text x="${horizontal + 100}" y="${vertical + 31}" text-anchor="middle" font-size="18" font-weight="600">${escapeXml(label)}</text><text x="${horizontal + 100}" y="${vertical + 57}" text-anchor="middle" font-size="13" fill="#475569">${escapeXml(detail)}</text></g>`).join('\n');
const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1440" height="870" viewBox="0 0 1440 870"><defs><marker id="arrow" markerWidth="10" markerHeight="8" refX="9" refY="4" orient="auto"><path d="M0,0 L10,4 L0,8" fill="#94a3b8"/></marker></defs><rect width="1440" height="870" fill="#f8fafc"/><g font-family="Microsoft YaHei, sans-serif" fill="#0f172a"><text x="38" y="48" font-size="28" font-weight="700">zjfk · 资金风控项目架构</text><text x="38" y="78" font-size="14" fill="#64748b">Java 业务服务 + Python 模型服务 | 静态概念图 · 2026-09-14</text>${edgeSvg}${nodeSvg}<text x="38" y="825" font-size="14" fill="#64748b">箭头为依赖或概念关联；虚线为可选扩展。非运行时拓扑，详细证据与限制见项目介绍和图谱文档。</text></g></svg>`;
const vertices = overviewNodes.map(([id, label, detail, horizontal, vertical, color]) => `<mxCell id="${id}" value="${escapeXml(label + '&#xa;' + detail)}" style="rounded=1;whiteSpace=wrap;html=0;fillColor=${color};strokeColor=#94a3b8;fontSize=15;" vertex="1" parent="1"><mxGeometry x="${horizontal}" y="${vertical}" width="200" height="80" as="geometry"/></mxCell>`).join('\n');
const connectors = overviewEdges.map(([source, target, dashed], index) => `<mxCell id="edge${index}" style="edgeStyle=orthogonalEdgeStyle;endArrow=block;${dashed ? 'dashed=1;' : ''}" edge="1" parent="1" source="${source}" target="${target}"><mxGeometry relative="1" as="geometry"/></mxCell>`).join('\n');
const drawio = `<mxfile host="local"><diagram name="zjfk项目架构" id="zjfk-overview"><mxGraphModel dx="1440" dy="870" grid="1" gridSize="10" page="1" pageWidth="1440" pageHeight="870"><root><mxCell id="0"/><mxCell id="1" parent="0"/>${vertices}${connectors}</root></mxGraphModel></diagram></mxfile>`;
await fs.writeFile(path.join(output, '项目架构.svg'), svg + '\n', 'utf8');
await fs.writeFile(path.join(output, '项目架构.drawio'), drawio.replaceAll('&amp;#xa;', '&#xa;') + '\n', 'utf8');
