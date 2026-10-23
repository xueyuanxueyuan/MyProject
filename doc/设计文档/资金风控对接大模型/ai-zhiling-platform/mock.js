/* ============================================================================
 * mock.js — 演示数据唯一来源（应用清单 / 分类 / 统计口径）
 * ----------------------------------------------------------------------------
 * 【应用界面展示说明】
 *   - previewImage：应用界面截图（存放于 assets/preview/，按应用 id 命名）。
 *     未配置 embedUrl 的应用，进入使用后在应用界面页以截图展示系统界面；
 *     没有截图的应用展示「应用界面」占位容器，可随时补图。
 *   - embedUrl：实际应用访问地址。配置后优先以 iframe 整页加载真实系统
 *     （截图仅作未接入前的演示展示），并同步启用「新窗口打开」按钮。
 *   （注意：若目标系统设置了 X-Frame-Options / CSP 限制，需允许本门户域名嵌入）
 * ========================================================================== */
window.DB = {
  meta: {
    platform: 'AI智擎提效平台',
    org: '首信技术中台',
    slogan: '让每一个岗位，一键唤起 AI 生产力'
  },

  /* 两大板块 */
  boards: [
    {
      id: 'efficiency',
      name: 'AI提效工具集',
      short: '提效工具',
      desc: '面向产品、研发、测试、管理等岗位的智能体应用与成熟系统，统一入口、即点即用。'
    },
    {
      id: 'capability',
      name: 'AI能力集市',
      short: '能力集市',
      desc: 'AI 原子能力与通用技能库的统一开放出口：标准化服务接口对外开放，赋能上层智能应用快速集成。'
    }
  ],

  /* 三大门户端（默认技术中台；业务前端 / 管理后台暂不划分类别）
   * 应用的 portal 字段：'business' | 'tech' | 'admin'，缺省为 'tech'（技术中台）
   */
  portals: [
    { id: 'business', name: '业务前端', icon: 'briefcase', accent: '#5B8AF5', desc: '面向市场、招投标等业务场景的 AI 助手，助力业务拓展与经营决策' },
    { id: 'tech', name: '技术中台', icon: 'cpu', accent: '#17AD9B', desc: '面向产品、研发、测试、管理岗位的智能体应用与成熟系统，统一入口、即点即用' },
    { id: 'admin', name: '管理后台', icon: 'settings', accent: '#DB6DA6', desc: '面向行政、运营等管理场景的智能工具，让日常管理更省心' }
  ],

  /* 分类归集（board 归属两大板块；accent 为分类功能色，按浅色底对比度校准） */
  categories: [
    { id: 'mgmt',  name: '管理与运营类',     board: 'efficiency', accent: '#DB6DA6', icon: 'briefcase',
      desc: '工时测算、绩效考核、模型服务与研发效能的统一运营管控，让组织管理有据可依' },
    { id: 'rdt',   name: '需求、研发、测试类', board: 'efficiency', accent: '#5B8AF5', icon: 'code-2',
      desc: '需求工程与测试质量的智能辅助工具，以及智能体构建平台，分别提效研发各环节' },
    { id: 'know',  name: '知识与数据赋能类', board: 'efficiency', accent: '#2CA55F', icon: 'library',
      desc: '知识沉淀共享与高质量数据生产，让经验可复用、数据可评测' },
    { id: 'open',  name: '能力开放类',      board: 'capability', accent: '#F0703C', icon: 'share-2',
      desc: '标准化原子能力与统一服务接口，配套能力文档、演示、试用与开发指引一站式支撑' }
  ],

  /* 应用清单（14 个：11 个已上线 + 3 个敬请期待）——embedUrl 即实际应用嵌入入口
   * portal 字段：'business' / 'admin'，缺省视为 'tech'（技术中台）
   */
  apps: [
    /* ---- 管理与运营类 ---- */
    {
      id: 'teas',
      name: '需求工时智能评估系统',
      category: 'mgmt',
      icon: 'calculator',
      desc: 'AI 解析需求材料，结合 FPA、WBS 与 PERT，形成可追溯的 P50 / P80 工时、人员及费用估算，支撑项目预算与资源配置决策。',
      tags: ['AI解析', 'FPA估算', 'P50/P80', '预算决策'],
      usage: 987, users: 158, delta: 9.3, heat: 64,
      previewImage: 'assets/preview/teas.png',
      embedUrl: ''
    },
    {
      id: 'perf-mgmt',
      name: '智能绩效管理平台',
      category: 'mgmt',
      icon: 'gauge',
      desc: '一站式智能绩效管理：日常工作进度与成果记录沉淀，考核指标灵活配置、智能评分、员工互评与结果汇总可视化。',
      tags: ['指标配置', '智能评分', '员工互评', '结果看板'],
      usage: 1654, users: 486, delta: 6.5, heat: 73,
      previewImage: 'assets/preview/perf-mgmt.png',
      embedUrl: ''
    },
    {
      id: 'token-factory',
      name: 'Token工厂',
      category: 'mgmt',
      icon: 'factory',
      desc: '公司统一的大模型服务门户：员工自助申请、免费使用，管理员统一管控额度、查看用量并完整留存对话记录——统一出口、统一管理、统一审计。',
      tags: ['大模型服务', '统一出口', '额度管控', '用量审计'],
      usage: 18652, users: 1842, delta: 21.7, heat: 98,
      previewImage: 'assets/preview/token-factory.png',
      embedUrl: ''
    },
    {
      id: 'eff-metrics',
      name: '效能指标检测平台',
      category: 'mgmt',
      icon: 'activity',
      desc: '面向研发组织的 AI 辅助编程效能监测与数据洞察平台：自动留痕、精准识别、多维下钻，从公司到部门、项目、人和每一次提交。',
      tags: ['效能留痕', '多维下钻', '提交识别'],
      usage: 2958, users: 187, delta: 18.6, heat: 84,
      previewImage: 'assets/preview/eff-metrics.png',
      embedUrl: ''
    },

    /* ---- 需求、研发、测试类（按需求→研发→测试排序） ---- */
    {
      id: 'req-assistant',
      name: '智能需求助手',
      category: 'rdt',
      icon: 'clipboard-list',
      desc: 'AI 驱动的需求工程平台：从资料建档、流程识别、口径澄清，到原型、PRD 与评审，把模糊需求沉淀成可确认、可交付的结果。',
      tags: ['需求澄清', '原型生成', 'PRD', '评审辅助'],
      usage: 9437, users: 612, delta: 15.1, heat: 95,
      previewImage: 'assets/preview/req-assistant.png',
      embedUrl: '' // TODO: 填入实际应用地址，如 'https://...'
    },

    {
      id: 'agent-dev',
      name: '智能体开发平台',
      category: 'rdt',
      icon: 'bot',
      desc: '企业级智能体开发与运营一体化平台：可视化构建工作台管理自主规划型、主从协作型、工作流型三类智能体，支撑快速搭建、配置与迭代落地。',
      tags: ['可视化构建', '三类智能体', '运营管理'],
      usage: 2341, users: 163, delta: 16.9, heat: 81,
      previewImage: 'assets/preview/agent-dev.png',
      embedUrl: ''
    },
    {
      id: 'auto-test',
      name: '智测平台',
      category: 'rdt',
      icon: 'flask',
      desc: 'UI 自动化用例一站式管理、执行、报表、监控与定时调度，回归测试全自动运转，质量状态一目了然。',
      tags: ['UI自动化', '用例管理', '定时执行', '测试报表'],
      usage: 3216, users: 268, delta: 8.2, heat: 87,
      previewImage: 'assets/preview/auto-test.png',
      embedUrl: ''
    },

    /* ---- 知识与数据赋能类 ---- */
    {
      id: 'kb-platform',
      name: '知识库平台',
      category: 'know',
      icon: 'library',
      desc: '围绕「知识管理—知识构建—知识服务」全链路闭环：知识归集、加工、安全管控、智能检索与问答交互，构建安全可靠的底层知识支撑底座。',
      tags: ['知识归集', '智能检索', '问答交互', '安全管控'],
      usage: 8893, users: 754, delta: 13.2, heat: 93,
      previewImage: 'assets/preview/kb-platform.png',
      embedUrl: ''
    },
    {
      id: 'coding-know',
      name: 'AICoding知识共享平台',
      category: 'know',
      icon: 'book-open',
      desc: '汇集行业动态、成果方法、培训资料及模型与工具选型：同一入口查阅资讯、下载方法资料，并对照完成模型与工具选型。',
      tags: ['行业动态', '方法沉淀', '培训资料', '工具选型'],
      usage: 1423, users: 341, delta: 4.8, heat: 70,
      previewImage: 'assets/preview/coding-know.png',
      embedUrl: ''
    },
    {
      id: 'data-label',
      name: 'AI数据标注平台',
      category: 'know',
      icon: 'tags',
      desc: '面向团队协作的数据标注管理系统：以「场景」为任务单元，覆盖数据集导入、标注执行、进度跟踪与质量评测，支持成员协作与角色分配。',
      tags: ['场景标注', '数据集管理', '团队协作', '质量评测'],
      usage: 1876, users: 132, delta: 12.4, heat: 76,
      previewImage: 'assets/preview/data-label.png',
      embedUrl: ''
    },

    /* ---- 能力开放类（AI能力集市板块） ---- */
    {
      id: 'cap-open',
      name: 'AI能力开放展示平台',
      category: 'open',
      icon: 'share-2',
      desc: '标准化、可复用的通用技能库与 AI 底层原子能力：统一能力服务接口对外开放，配套能力文档、在线演示、开放试用与开发指引一站式支撑。',
      tags: ['原子能力', 'Skills技能库', '服务接口', '开放试用'],
      usage: 764, users: 96, delta: 7.4, heat: 58,
      previewImage: 'assets/preview/cap-open.png',
      embedUrl: ''
    },

    /* ---- 业务前端（暂不划分类别，均为敬请期待） ---- */
    {
      id: 'bid-assistant',
      name: '招投标助手',
      portal: 'business',
      category: null,
      icon: 'file-text',
      desc: '面向招投标业务场景的 AI 助手：标书解读、要点提取与合规初检，助力投标提效。',
      tags: ['标书解读', '要点提取', '合规初检'],
      usage: 0, users: 0, delta: 0, heat: 0,
      embedUrl: '',
      soon: true
    },
    {
      id: 'market-analysis',
      name: '市场分析工具',
      portal: 'business',
      category: null,
      icon: 'pie-chart',
      desc: '面向市场团队的智能分析工具：行业数据洞察与竞品动态追踪，辅助市场经营决策。',
      tags: ['行业洞察', '竞品追踪'],
      usage: 0, users: 0, delta: 0, heat: 0,
      embedUrl: '',
      soon: true
    },

    /* ---- 管理后台（暂不划分类别，均为敬请期待） ---- */
    {
      id: 'meeting-room',
      name: '会议室预定',
      portal: 'admin',
      category: null,
      icon: 'calendar',
      desc: '会议室在线查询与一键预定，支持常用会议室收藏与时段冲突提醒。',
      tags: ['在线预定', '冲突提醒'],
      usage: 0, users: 0, delta: 0, heat: 0,
      embedUrl: '',
      soon: true
    }
  ],

  /* 近 14 天平台使用趋势（模拟） */
  trend: [
    { date: '08/22', count: 612 },
    { date: '08/23', count: 658 },
    { date: '08/24', count: 701 },
    { date: '08/25', count: 645 },
    { date: '08/26', count: 730 },
    { date: '08/27', count: 788 },
    { date: '08/28', count: 754 },
    { date: '08/29', count: 812 },
    { date: '08/30', count: 869 },
    { date: '08/31', count: 903 },
    { date: '09/01', count: 845 },
    { date: '09/02', count: 921 },
    { date: '09/03', count: 968 },
    { date: '09/04', count: 1024 }
  ],

  /* 全局统计口径（模拟） */
  overview: {
    monthlyUsers: 2347,  // 月活跃用户
    usersDelta: 5.2,     // 月活环比
    totalDelta: 3.8,    // 累计使用环比
    trendDelta: 9.6     // 近 14 天环比
  }
};

/* ============================================================================
 * 初始化阶段：给所有应用批量补齐 4 个版本字段
 * （保证应用中心卡片 / 能力管理列表 / 详情弹窗首次进入即有版本信息）
 * -------------------------------------------------------------------------- */
(function () {
  var VER_POOL_MAJOR = [1, 1, 2, 1, 3, 2, 1, 2, 1, 1, 2, 1, 1, 1];
  var VER_POOL_MINOR = [0, 2, 1, 3, 0, 2, 4, 1, 0, 2, 1, 0, 0, 0];
  var VER_POOL_PATCH = [0, 1, 0, 2, 0, 3, 1, 0, 1, 0, 0, 0, 0, 0];
  var OWNERS = ['产品经理·小王', '研发主管·老李', '运营·小周', '架构师·老刘', '测试主管·小张'];
  var NOTES = [
    '基础功能稳定版；修复若干小缺陷与体验优化。',
    '新增分类筛选与收藏夹；首页加载提速 30%。',
    '报表模块上线；修复明细导出截断问题。',
    '适配深色模式；升级底层依赖至最新稳定版。',
    '权限模型重构；新增细粒度角色与批量审批流。',
    ''
  ];
  function pad(n) { return n < 10 ? '0' + n : '' + n; }
  function fmtDate(shiftDay, h, m) {
    var d = new Date(2026, 8, 10 + shiftDay, h, m, 0);
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) +
      ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
  }
  DB.apps = DB.apps.map(function (a, i) {
    if (a.version) return a; /* 已人工填写则跳过 */
    a.version = 'v' + (VER_POOL_MAJOR[i % VER_POOL_MAJOR.length]) +
      '.' + (VER_POOL_MINOR[i % VER_POOL_MINOR.length]) +
      '.' + (VER_POOL_PATCH[i % VER_POOL_PATCH.length]);
    a.updateTime = fmtDate(i, 9 + (i % 8), 10 + (i * 7) % 50);
    a.releaseOwner = OWNERS[i % OWNERS.length];
    a.releaseNote = (i % 3 === 0) ? NOTES[i % NOTES.length] : '';
    return a;
  });
})();
