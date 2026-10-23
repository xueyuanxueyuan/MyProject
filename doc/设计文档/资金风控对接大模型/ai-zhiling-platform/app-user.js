/* ============================================================================
 * app-user.js —— 统一认证登录 + 用户管理（后台）
 * 依赖：icons.js (icon/$$/$/toast)、styles.css、index.html 中预留 #userSlot/#view-users
 * ========================================================================== */
(function () {
  'use strict';

  /* ============================================================
   * 1. 常量 / 字典 / 默认数据
   * ========================================================== */
  var STORAGE_KEY = 'aizhi_session_v1';
  var STORAGE_USERS = 'aizhi_users_v1';
  var STORAGE_LOGS = 'aizhi_userlogs_v1';
  var CAPTCHA_EXPIRE_MS = 2 * 60 * 1000;
  var EMAIL_CODE_EXPIRE_MS = 5 * 60 * 1000;
  var EMAIL_SEND_COOLDOWN = 60;

  var ROLE = {
    SUPER:  'super',
    CONFIG: 'config',
    NORMAL: 'normal'
  };
  var ROLE_NAME = { super:'超级管理员', config:'配置管理员', normal:'普通用户' };
  var ROLE_CLASS = { super:'role-super', config:'role-config', normal:'role-normal' };
  var STATUS = { ACTIVE:'active', DISABLED:'disabled', DELETED:'deleted' };
  var STATUS_NAME = { active:'启用', disabled:'禁用', deleted:'销户' };
  var PERSON_TYPE = ['公司职工', '外包人员', '其他'];
  var DEPTS = [
    '公司领导', '技术中台部', '业务平台部', '产品管理部',
    '市场营销部', '行政管理部', '人力资源部', '财务部', '质量管理部'
  ];

  var DEFAULT_USERS = [
    { id:'u001', username:'admin',     password:'Cap2026', name:'系统管理员', dept:'技术中台部', empNo:'E00001', phone:'13800000001', email:'admin@capinfo.com.cn', person:'公司职工', role:ROLE.SUPER,  status:STATUS.ACTIVE,   createdAt:'2026-08-15 09:20:00', deletedReserved:false },
    { id:'u002', username:'config01',  password:'Config@01',  name:'配置管理员·小王', dept:'产品管理部', empNo:'E00032', phone:'13800000032', email:'config01@capinfo.com.cn', person:'公司职工', role:ROLE.CONFIG, status:STATUS.ACTIVE, createdAt:'2026-08-20 14:05:12', deletedReserved:false },
    { id:'u003', username:'zhangsan',  password:'Zs@2026ok',  name:'张三', dept:'业务平台部', empNo:'E00118', phone:'13800000118', email:'zhangsan@capinfo.com.cn', person:'公司职工', role:ROLE.NORMAL, status:STATUS.ACTIVE,   createdAt:'2026-09-02 10:32:41', deletedReserved:false },
    { id:'u004', username:'lisi',      password:'Ls!123456',  name:'李四', dept:'技术中台部', empNo:'E00207', phone:'13800000207', email:'lisi@capinfo.com.cn',     person:'公司职工', role:ROLE.NORMAL, status:STATUS.ACTIVE,   createdAt:'2026-09-10 09:47:28', deletedReserved:false },
    { id:'u005', username:'wangwu',    password:'Ww@Abc123',  name:'王五', dept:'质量管理部', empNo:'E00213', phone:'13800000213', email:'wangwu@capinfo.com.cn',   person:'外包人员', role:ROLE.NORMAL, status:STATUS.ACTIVE, createdAt:'2026-09-12 16:14:02', deletedReserved:false },
    { id:'u006', username:'demo_guest',password:'',           name:'演示·已禁用', dept:'行政管理部', empNo:'', phone:'', email:'demo_g@capinfo.com.cn', person:'其他', role:ROLE.NORMAL, status:STATUS.DISABLED,  createdAt:'2026-09-18 11:20:00', deletedReserved:false, disableReason:'多次违规操作' },
    { id:'u007', username:'old_ghost', password:'',           name:'老账号·已销户', dept:'财务部', empNo:'E00004', phone:'', email:'old@capinfo.com.cn',    person:'公司职工', role:ROLE.NORMAL, status:STATUS.DELETED, createdAt:'2026-05-08 13:30:00', deletedReserved:true, deleteReason:'离职销户' }
  ];
  var DEFAULT_LOGS = [
    { time:'2026-10-08 15:22:09', user:'admin', ip:'10.12.3.45', type:'登录',   obj:'会话', diff:'账号登录成功' },
    { time:'2026-10-08 11:05:41', user:'admin', ip:'10.12.3.45', type:'禁用',   obj:'用户：demo_guest', diff:'禁用原因：多次违规操作' },
    { time:'2026-10-07 18:33:10', user:'config01', ip:'10.12.5.12', type:'改密', obj:'本账号', diff:'自主修改密码成功' },
    { time:'2026-10-06 10:08:52', user:'admin', ip:'10.12.3.45', type:'新增',   obj:'用户：wangwu', diff:'创建账号，初始密码已发送邮箱' },
    { time:'2026-10-01 09:00:00', user:'admin', ip:'10.12.3.45', type:'销户',   obj:'用户：old_ghost', diff:'离职销户，备注：人员离司统一回收账号' },
    { time:'2026-09-28 14:22:15', user:'zhangsan', ip:'10.12.4.77', type:'注册', obj:'账号注册', diff:'邮箱注册：zhangsan@capinfo.com.cn' }
  ];

  /* 密码校验类型 */
  var PWS = function(p){
    return {
      len: p.length>=6 && p.length<=30,
      lower: /[a-z]/.test(p),
      upper: /[A-Z]/.test(p),
      num:   /[0-9]/.test(p),
      sp:    /[!@#$%^&*]/.test(p)
    };
  };
  function passwordScore(p){
    var r = PWS(p);
    if(!r.len) return { score:0, ok:false, cls:'', label:'6~30 位' };
    var kinds = (r.lower?1:0)+(r.upper?1:0)+(r.num?1:0)+(r.sp?1:0);
    var ok = kinds>=3 && r.len;
    var cls = kinds<=1?'weak':(kinds===2?'mid':'ok');
    var label = ok ? ('强度：'+(kinds===3?'中':'强')) : ('需包含 3 类及以上字符（已 '+kinds+' 类）');
    return { score:kinds, ok:ok, cls:cls, label:label };
  }
  function genRandomPw(){
    var s = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789',
        sp = '!@#$%^&*', r = '';
    for (var i=0;i<3;i++) r += s[Math.floor(Math.random()*24)];
    for (var j=0;j<3;j++) r += s[24+Math.floor(Math.random()*26)];
    for (var k=0;k<2;k++) r += s[50+Math.floor(Math.random()*8)];
    r += sp[Math.floor(Math.random()*sp.length)];
    return r.split('').sort(function(){return Math.random()-0.5}).join('');
  }
  function genCaptcha(){
    var s = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789';
    var r = ''; for (var i=0;i<4;i++) r += s[Math.floor(Math.random()*s.length)];
    return r;
  }
  function genEmailCode(){
    var r = ''; for (var i=0;i<6;i++) r += Math.floor(Math.random()*10);
    return r;
  }
  function fmtNow(){ return new Date().toISOString().slice(0,19).replace('T',' '); }
  function randomIP(){ return '10.12.'+ (2+Math.floor(Math.random()*8)) + '.' + (10+Math.floor(Math.random()*240)); }
  function randId(prefix){ return (prefix||'u') + Math.random().toString(36).slice(2,8)+Date.now().toString(36).slice(-3); }

  /* 邮箱/手机/用户名 格式 */
  function isEmail(s){ return /^[\w.+-]+@[\w-]+\.[\w.-]+$/.test(s); }
  function isPhone(s){ return /^1[3-9]\d{9}$/.test(s); }
  function isUsername(s){ return /^[A-Za-z0-9]{3,24}$/.test(s); }

  /* ============================================================
   * 2. 持久化包装
   * ========================================================== */
  function load(key, fallback){
    try {
      var raw = localStorage.getItem(key);
      if(!raw) return JSON.parse(JSON.stringify(fallback));
      return JSON.parse(raw);
    } catch(e){ return JSON.parse(JSON.stringify(fallback)); }
  }
  function save(key, val){ try{ localStorage.setItem(key, JSON.stringify(val)); }catch(e){} }

  var U = {
    session: load(STORAGE_KEY, null), /* 首次进入强制游客态，必须使用登录弹窗完成身份验证 */
    users:   load(STORAGE_USERS, DEFAULT_USERS),
    logs:    load(STORAGE_LOGS, DEFAULT_LOGS)
  };
  function persistAll(){ save(STORAGE_KEY, U.session); save(STORAGE_USERS, U.users); save(STORAGE_LOGS, U.logs); }

  /* 图形验证码与邮箱验证码（内存） */
  var captcha = { text: genCaptcha(), expireAt: Date.now()+CAPTCHA_EXPIRE_MS };
  function refreshCaptcha(){ captcha.text = genCaptcha(); captcha.expireAt = Date.now()+CAPTCHA_EXPIRE_MS; }
  function captchaValid(input){
    if(Date.now()>captcha.expireAt) return '验证码已过期，请刷新';
    if(String(input||'').toLowerCase() !== String(captcha.text||'').toLowerCase()) return '图形验证码错误（不区分大小写）';
    return null;
  }
  var emailCodeMap = {}; // { [email]: {code, expireAt} }
  var emailCoolDown = {}; // { [email]: timerSeconds }
  function setEmailCode(email, code){
    emailCodeMap[email] = { code: String(code), expireAt: Date.now()+EMAIL_CODE_EXPIRE_MS };
  }
  function emailCodeValid(email, input){
    var rec = emailCodeMap[email];
    if(!rec) return '未发送验证码或已失效';
    if(Date.now()>rec.expireAt) return '邮箱验证码已过期';
    if(rec.code !== String(input||'')) return '邮箱验证码错误';
    return null;
  }

  /* ============================================================
   * 3. 日志记录 + 工具
   * ========================================================== */
  function addLog(type, obj, diff){
    var cur = U.session && U.session.username;
    U.logs.unshift({
      time: fmtNow(),
      user: cur || 'guest',
      ip: randomIP(),
      type: type,
      obj: obj,
      diff: diff || ''
    });
    persistAll();
  }
  function usernameTaken(name, ignoreId){
    return !!U.users.find(function(u){ return u.username===name && u.id!==ignoreId; });
  }
  function emailTaken(email, ignoreId){
    return !!U.users.find(function(u){ return u.email===email && u.id!==ignoreId; });
  }
  function roleOf(u){ return u ? u.role : null; }
  function isGuest(){ return !U.session; }
  function hasPerm(need){
    if(need==='config') return roleOf(U.session && findUser(U.session.username)) === ROLE.SUPER || roleOf(U.session && findUser(U.session.username)) === ROLE.CONFIG;
    if(need==='super')  return roleOf(U.session && findUser(U.session.username)) === ROLE.SUPER;
    return !!U.session;
  }
  function findUser(name){ return U.users.find(function(u){ return u.username===name; }); }
  function findUserById(id){ return U.users.find(function(u){ return u.id===id; }); }

  /* ============================================================
   * 4. 顶部用户胶囊渲染（登录按钮/用户菜单）
   * ========================================================== */
  function renderUserSlot(){
    var slot = document.getElementById('userSlot');
    if(!slot) return;
    slot.innerHTML = '';
    if(isGuest()){
      var btn = document.createElement('button');
      btn.className = 'btn-login';
      btn.innerHTML = (window.icon ? icon('log-in',14)+' ' : '') + '登录 / 注册';
      btn.onclick = function(){ openAuthDialog('login'); };
      slot.appendChild(btn);
    } else {
      var u = findUser(U.session.username) || {};
      var chip = document.createElement('div');
      chip.className = 'user-chip';
      var roleTag = '';
      if(u.role===ROLE.SUPER) roleTag = '<span class="us-role">超级管理员</span>';
      else if(u.role===ROLE.CONFIG) roleTag = '<span class="us-role">配置管理员</span>';
      chip.innerHTML =
        '<span data-icon="user" data-size="15"></span>' +
        '<span class="user-name">'+esc(u.name||U.session.username)+'</span>' + roleTag +
        '<span data-icon="chevron-down" data-size="12" style="margin-left:6px;color:var(--text-dim)"></span>';
      chip.onclick = function(e){
        e.stopPropagation();
        toggleUserMenu(chip, u);
      };
      slot.appendChild(chip);
    }
    applyNavPerm();
    if(typeof refreshIcons === 'function') refreshIcons();
  }
  function toggleUserMenu(anchor, u){
    var existed = document.querySelector('.user-menu');
    if(existed){ existed.remove(); return; }
    var menu = document.createElement('div');
    menu.className = 'user-menu';
    var items = [
      { ic:'user', label:'个人中心：'+ (u.name||u.username) },
      { ic:'lock', label:'修改密码', act:'pwd' }
    ];
    if(u.role===ROLE.SUPER)  items.push({ ic:'users', label:'用户管理', act:'users' });
    if(u.role===ROLE.CONFIG || u.role===ROLE.SUPER) items.push({ ic:'settings', label:'能力管理', act:'ability' });
    items.push({ divider:true });
    items.push({ ic:'log-out', label:'退出登录', act:'logout', danger:true });
    var html = '';
    items.forEach(function(it){
      if(it.divider){ html += '<div class="um-divider"></div>'; return; }
      html += '<div class="um-item'+(it.danger?' danger':'')+'" data-act="'+(it.act||'')+'">'+
                (window.icon?icon(it.ic||'user',13):'')+ it.label +
              '</div>';
    });
    menu.innerHTML = html;
    document.body.appendChild(menu);
    var rect = anchor.getBoundingClientRect();
    menu.style.top = (rect.bottom+window.scrollY+6)+'px';
    menu.style.right = (window.innerWidth - rect.right - 14 + window.scrollX)+'px';
    menu.querySelectorAll('.um-item').forEach(function(el){
      el.onclick = function(){
        var a = el.dataset.act;
        menu.remove();
        if(a==='pwd')    openChangePwdDialog();
        if(a==='users')  location.hash = '#users';
        if(a==='ability')location.hash = '#ability';
        if(a==='logout') doLogout();
      };
    });
    setTimeout(function(){
      document.addEventListener('click', function _h(e){
        if(menu.contains(e.target)||e.target===anchor) return;
        menu.remove(); document.removeEventListener('click',_h,true);
      }, true);
    }, 10);
  }
  function doLogout(){
    addLog('登出', '会话', '用户已退出登录');
    U.session = null; persistAll();
    renderUserSlot();
    renderGuestBanner();
    if(location.hash==='#ability' || location.hash==='#users') location.hash = '#apps';
    else renderUsersPage();
    toast('已退出登录');
  }

  /* 权限：顶部主菜单显隐 */
  function applyNavPerm(){
    $$('.main-nav a[data-perm]').forEach(function(a){
      var need = a.dataset.perm;
      if(!hasPerm(need)){ a.style.display = 'none'; a.classList.remove('active'); }
      else { a.style.display = ''; }
    });
  }

  /* 游客提示横幅 */
  function renderGuestBanner(){
    var prev = document.querySelector('.guest-banner');
    if(prev) prev.remove();
    if(!isGuest()) return;
    var apps = document.getElementById('view-apps');
    if(!apps) return;
    var banner = document.createElement('div');
    banner.className = 'guest-banner';
    banner.innerHTML =
      '<div class="gb-left">' +
        (window.icon?icon('info',14)+' ':'') +
        '您当前以 <b>游客身份</b> 浏览，仅可查看公开应用；登录后可访问更多能力，<b>管理员</b>可进入能力配置与用户管理后台。' +
      '</div>' +
      '<div>' +
        '<button class="btn-secondary" id="gbLogin" style="margin-right:8px">'+(window.icon?icon('log-in',13)+' ':'')+'登录</button>' +
        '<button class="btn-primary" id="gbReg">'+(window.icon?icon('user-plus',13)+' ':'')+'注册账号</button>' +
      '</div>';
    apps.parentNode.insertBefore(banner, apps);
    document.getElementById('gbLogin').onclick = function(){ openAuthDialog('login'); };
    document.getElementById('gbReg').onclick   = function(){ openAuthDialog('register'); };
  }

  /* ============================================================
   * 5. 统一认证弹窗（登录 / 注册 / 忘记密码 Tab）
   * ========================================================== */
  var authDlg = null;
  function openAuthDialog(tab){
    closeAuthDialog();
    tab = tab || 'login';
    authDlg = document.createElement('div');
    authDlg.className = 'dialog-mask';
    authDlg.innerHTML =
      '<div class="dialog" style="width:480px">' +
        '<div class="dialog-head"><h3>'+(window.icon?icon('shield-check',16)+' ':'')+'统一身份认证</h3><button class="dialog-close" data-x>&times;</button></div>' +
        '<div class="dialog-body">' +
          '<div class="auth-tabs">' +
            '<div class="at'+(tab==='login'?' active':'')+'" data-t="login">登录</div>' +
            '<div class="at'+(tab==='register'?' active':'')+'" data-t="register">注册</div>' +
            '<div class="at'+(tab==='forgot'?' active':'')+'" data-t="forgot">忘记密码</div>' +
          '</div>' +
          '<div class="auth-body" id="authBody"></div>' +
        '</div>' +
      '</div>';
    document.body.appendChild(authDlg);
    authDlg.addEventListener('click', function(e){ if(e.target===authDlg) closeAuthDialog(); });
    authDlg.querySelector('[data-x]').onclick = closeAuthDialog;
    authDlg.querySelectorAll('.at').forEach(function(el){
      el.onclick = function(){
        authDlg.querySelectorAll('.at').forEach(function(x){ x.classList.remove('active'); });
        el.classList.add('active');
        renderAuthBody(el.dataset.t);
      };
    });
    renderAuthBody(tab);
  }
  function closeAuthDialog(){ if(authDlg){ authDlg.remove(); authDlg=null; } }
  function renderAuthBody(tab){
    var body = authDlg.querySelector('#authBody');
    if(tab==='login')    body.innerHTML = renderLoginTpl();
    if(tab==='register') body.innerHTML = renderRegisterTpl();
    if(tab==='forgot')   body.innerHTML = renderForgotTpl();
    bindAuthBodyEvents(tab);
    if(typeof refreshIcons === 'function') refreshIcons();
  }
  function renderLoginTpl(){
    refreshCaptcha();
    return (
      '<div class="form-grid">' +
        '<div class="fg-item full"><label>用户名</label><input id="f_u" placeholder="输入用户名" autocomplete="username"></div>' +
        '<div class="fg-item full"><label>登录密码</label><input id="f_p" type="password" placeholder="输入密码" autocomplete="current-password"></div>' +
        '<div class="fg-item full"><label>图形验证码</label>' +
          '<div class="captcha-row">' +
            '<input id="f_cap" maxlength="4" placeholder="不区分大小写" autocomplete="off">' +
            '<div class="captcha-img" id="capImg">'+esc(captcha.text)+'</div>' +
          '</div>' +
          '<span class="fg-hint" id="capHint">点击可刷新；有效期 2 分钟</span>' +
        '</div>' +
        '<div class="fg-item full" style="text-align:right"><a href="javascript:;" style="color:var(--primary);font-size:12px" id="goForgot">忘记密码？</a></div>' +
      '</div>' +
      '<div style="margin-top:10px;display:flex;gap:10px;justify-content:flex-end">' +
        '<button class="btn-secondary" data-act="cancel">取消</button>' +
        '<button class="btn-primary" data-act="login">'+(window.icon?icon('log-in',14)+' ':'')+'登录</button>' +
      '</div>'
    );
  }
  function renderRegisterTpl(){
    return (
      '<div class="form-grid">' +
        '<div class="fg-item"><label>用户名 <span class="req">*</span></label><input id="r_u" maxlength="24"><span class="fg-hint" id="r_u_h">字母数字，3~24 位，全局唯一</span></div>' +
        '<div class="fg-item"><label>真实姓名 <span class="req">*</span></label><input id="r_name" maxlength="20"></div>' +
        '<div class="fg-item"><label>所属部门 <span class="req">*</span></label><select id="r_dept">'+DEPTS.map(function(d){return '<option>'+esc(d)+'</option>'}).join('')+'</select></div>' +
        '<div class="fg-item"><label>员工工号</label><input id="r_emp" maxlength="16"><span class="fg-hint">公司职工必填，外包/其他可空</span></div>' +
        '<div class="fg-item"><label>人员性质</label><select id="r_person">'+PERSON_TYPE.map(function(p){return '<option>'+esc(p)+'</option>'}).join('')+'</select></div>' +
        '<div class="fg-item"><label>手机号码 <span class="req">*</span></label><input id="r_phone" maxlength="11"></div>' +
        '<div class="fg-item full"><label>邮箱地址 <span class="req">*</span></label><input id="r_email"><span class="fg-hint" id="r_email_h">用于接收验证码、找回密码；全局唯一</span></div>' +
        '<div class="fg-item full"><label>邮箱验证码 <span class="req">*</span></label>' +
          '<div class="code-row">' +
            '<input id="r_code" maxlength="6" placeholder="输入 6 位验证码">' +
            '<button class="btn-code" id="r_send">发送验证码</button>' +
          '</div>' +
          '<span class="fg-hint">验证码 5 分钟有效；每 60s 可重发一次</span>' +
        '</div>' +
        '<div class="fg-item full"><label>登录密码 <span class="req">*</span></label><input id="r_pw" type="password" maxlength="30">' +
          '<div class="pw-strength"><i class="pw-bar" id="pw1"></i><i class="pw-bar" id="pw2"></i><i class="pw-bar" id="pw3"></i><i class="pw-bar" id="pw4"></i></div>' +
          '<span class="pw-label" id="pwLabel">6~30 位，数字/大写/小写/特殊字符 3 类及以上组合</span>' +
        '</div>' +
        '<div class="fg-item full"><label>确认密码 <span class="req">*</span></label><input id="r_pw2" type="password" maxlength="30"><span class="fg-hint" id="pw2Hint">两次输入必须一致</span></div>' +
      '</div>' +
      '<div style="margin-top:10px;display:flex;gap:10px;justify-content:flex-end">' +
        '<button class="btn-secondary" data-act="cancel">取消</button>' +
        '<button class="btn-primary" data-act="register">'+(window.icon?icon('user-plus',14)+' ':'')+'提交注册</button>' +
      '</div>'
    );
  }
  function renderForgotTpl(){
    return (
      '<div class="form-grid">' +
        '<div class="fg-item full"><label>用户名 <span class="req">*</span></label><input id="f_u2" placeholder="需要找回的账号"></div>' +
        '<div class="fg-item full"><label>验证邮箱 <span class="req">*</span></label><input id="f_email2" placeholder="该账号绑定的邮箱地址"><span class="fg-hint" style="color:#E64448">※ 暂不支持手机号码找回密码</span></div>' +
        '<div class="fg-item full"><label>邮箱验证码 <span class="req">*</span></label>' +
          '<div class="code-row">' +
            '<input id="f_code2" maxlength="6">' +
            '<button class="btn-code" id="f_send2">发送验证码</button>' +
          '</div>' +
        '</div>' +
        '<div class="fg-item full"><label>设置新密码 <span class="req">*</span></label><input id="f_newPw" type="password" maxlength="30">' +
          '<div class="pw-strength"><i class="pw-bar" id="fpw1"></i><i class="pw-bar" id="fpw2"></i><i class="pw-bar" id="fpw3"></i><i class="pw-bar" id="fpw4"></i></div>' +
          '<span class="pw-label" id="fpwLabel">6~30 位，数字/大写/小写/特殊字符 3 类及以上组合</span>' +
        '</div>' +
        '<div class="fg-item full"><label>确认新密码 <span class="req">*</span></label><input id="f_newPw2" type="password" maxlength="30"></div>' +
      '</div>' +
      '<div style="margin-top:10px;display:flex;gap:10px;justify-content:flex-end">' +
        '<button class="btn-secondary" data-act="cancel">取消</button>' +
        '<button class="btn-primary" data-act="forgot">'+(window.icon?icon('key',14)+' ':'')+'提交重置</button>' +
      '</div>'
    );
  }
  function paintPwStrength(val, ids){
    var s = passwordScore(val);
    for(var i=1;i<=4;i++){
      var el = document.getElementById(ids+(i));
      if(!el) continue;
      el.className = 'pw-bar' + (i<=s.score ? ' on '+s.cls : '');
    }
    return s;
  }
  function sendEmailCode(email, btnId, mode){
    if(emailCoolDown[email] && emailCoolDown[email]>0){ toast('发送过于频繁，请稍后'); return; }
    if(!isEmail(email)){ toast('邮箱格式不正确'); return false; }
    if(mode==='register' && emailTaken(email)){ toast('邮箱已被注册'); return false; }
    var code = genEmailCode();
    setEmailCode(email, code);
    addLog('发送验证码', '邮箱：'+email, (mode==='register'?'注册验证码：':'找回密码验证码：')+ '已发送（演示：'+code+'）');
    toast('验证码已发送至邮箱：'+email+'（演示：'+code+'）');
    var btn = document.getElementById(btnId);
    var left = EMAIL_SEND_COOLDOWN;
    emailCoolDown[email] = left;
    if(btn) btn.disabled = true;
    var t = setInterval(function(){
      left--; emailCoolDown[email] = left;
      if(btn) btn.textContent = left+'s 后重发';
      if(left<=0){ clearInterval(t); emailCoolDown[email]=0; if(btn){ btn.disabled=false; btn.textContent='重新发送';} }
    }, 1000);
    return true;
  }
  function bindAuthBodyEvents(tab){
    var dlg = authDlg;
    dlg.querySelector('[data-act="cancel"]').onclick = closeAuthDialog;
    if(tab==='login'){
      dlg.querySelector('#capImg').onclick = function(){ refreshCaptcha(); this.textContent = captcha.text; };
      dlg.querySelector('#goForgot').onclick = function(){
        dlg.querySelectorAll('.at').forEach(function(x){ x.classList.remove('active'); });
        dlg.querySelector('[data-t="forgot"]').classList.add('active');
        renderAuthBody('forgot');
      };
      dlg.querySelector('[data-act="login"]').onclick = function(){
        var u = $('#f_u').value.trim();
        var p = $('#f_p').value;
        var cap = $('#f_cap').value;
        var err = captchaValid(cap);
        if(err){ toast(err); refreshCaptcha(); dlg.querySelector('#capImg').textContent = captcha.text; return; }
        if(!u || !p){ toast('请完整输入用户名与密码'); return; }
        /* 超级管理员强校验（不依赖账号库，直接走统一认证） */
        var SUPER_USERS = [{ username:'admin', password:'Cap2026', role:ROLE.SUPER, name:'系统管理员' }];
        var superEntry = SUPER_USERS.find(function(s){ return s.username === u; });
        var user;
        if(superEntry){
          if(p !== superEntry.password){ addLog('登录失败','会话','超级管理员密码错误：'+u); refreshCaptcha(); dlg.querySelector('#capImg').textContent = captcha.text; toast('超级管理员用户名或密码错误（验证码已刷新）'); return; }
          /* 确保本地账号库同步最新密码，避免修改密码后校验分叉 */
          user = findUser(u) || { id:randId('u'), username:u, name:superEntry.name, dept:'技术中台部', empNo:'E00001', phone:'13800000001', email:'admin@capinfo.com.cn', person:'公司职工', createdAt:fmtNow(), deletedReserved:false };
          user.name = superEntry.name; user.role = superEntry.role; user.password = superEntry.password; user.status = STATUS.ACTIVE;
          if(!findUser(u)) U.users.unshift(user);
        } else {
          user = findUser(u);
          if(!user){ addLog('登录失败', '会话', '账号不存在：'+u); refreshCaptcha(); dlg.querySelector('#capImg').textContent = captcha.text; toast('用户名或密码错误（验证码已刷新）'); return; }
          if(user.status === STATUS.DISABLED){ addLog('登录失败', '会话', '账号禁用：'+u); toast('账号已被禁用，无法登录'); return; }
          if(user.status === STATUS.DELETED){  addLog('登录失败', '会话', '账号销户：'+u); toast('账号已注销，无法登录'); return; }
          if(user.password !== p){ addLog('登录失败', '会话', '密码错误：'+u); refreshCaptcha(); dlg.querySelector('#capImg').textContent = captcha.text; toast('用户名或密码错误（验证码已刷新）'); return; }
        }
        U.session = { username:user.username, loginAt:fmtNow(), ip:randomIP(), auth:'sso', verify:true };
        persistAll();
        addLog('登录', '会话', (superEntry?'[超级管理员] ':'') + '账号登录成功：' + user.name + '（' + user.username + '）');
        toast((superEntry?'【超级管理员】 ':'') + '登录成功：'+user.name);
        closeAuthDialog(); renderUserSlot(); renderGuestBanner(); renderUsersPage();
        applyNavPerm();
        /* hash 为配置页时需重新渲染以通过权限校验 */
        var h = (location.hash||'#apps').replace('#','');
        if(h==='ability' || h==='users'){ if(typeof handleHash==='function') handleHash(); }
        if(typeof renderAbility==='function' && h==='ability') try{ renderAbility(); }catch(e){}
      };
    }
    if(tab==='register'){
      var pw = $('#r_pw'), pw2=$('#r_pw2');
      if(pw) pw.addEventListener('input', function(){
        var s = paintPwStrength(this.value, 'pw');
        var lab = document.getElementById('pwLabel'); if(lab) lab.textContent = s.label; lab.className = 'pw-label' + (s.ok?' ok':' bad');
      });
      if(pw2) pw2.addEventListener('input', function(){
        var h = document.getElementById('pw2Hint');
        if(this.value && this.value !== pw.value){ h.textContent='两次密码不一致'; h.className='fg-hint bad'; }
        else if(this.value){ h.textContent='两次密码一致'; h.className='fg-hint ok'; }
        else { h.textContent='两次输入必须一致'; h.className='fg-hint'; }
      });
      dlg.querySelector('#r_send').onclick = function(){
        sendEmailCode($('#r_email').value.trim(), 'r_send', 'register');
      };
      dlg.querySelector('[data-act="register"]').onclick = function(){
        var username = $('#r_u').value.trim();
        var name = $('#r_name').value.trim();
        var dept = $('#r_dept').value;
        var emp = $('#r_emp').value.trim();
        var person = $('#r_person').value;
        var phone = $('#r_phone').value.trim();
        var email = $('#r_email').value.trim();
        var code = $('#r_code').value.trim();
        var pass = $('#r_pw').value;
        var pass2 = $('#r_pw2').value;
        if(!isUsername(username)){ toast('用户名仅允许字母数字，3~24 位'); return; }
        if(usernameTaken(username)){ toast('用户名已被占用'); return; }
        if(!name){ toast('请填写真实姓名'); return; }
        if(person==='公司职工' && !emp){ toast('公司职工必须填写工号'); return; }
        if(!isPhone(phone)){ toast('请输入正确的手机号码'); return; }
        if(!isEmail(email)){ toast('请输入正确的邮箱地址'); return; }
        if(emailTaken(email)){ toast('邮箱已被注册'); return; }
        var em = emailCodeValid(email, code);
        if(em){ toast(em); return; }
        var s = passwordScore(pass);
        if(!s.ok){ toast(s.label); return; }
        if(pass !== pass2){ toast('两次密码不一致'); return; }
        var u = { id:randId('u'), username:username, password:pass, name:name, dept:dept, empNo:emp, phone:phone, email:email, person:person, role:ROLE.NORMAL, status:STATUS.ACTIVE, createdAt:fmtNow(), deletedReserved:false };
        U.users.unshift(u);
        addLog('注册', '用户：'+username, '邮箱注册：'+email+'，角色：普通用户');
        persistAll();
        closeAuthDialog();
        showResultDialog('注册成功', '账号 '+username+' 已创建，请妥善保管密码。', pass);
      };
    }
    if(tab==='forgot'){
      var fp = $('#f_newPw');
      if(fp) fp.addEventListener('input', function(){
        var s = paintPwStrength(this.value, 'fpw');
        var lab = document.getElementById('fpwLabel'); if(lab) lab.textContent = s.label; lab.className = 'pw-label' + (s.ok?' ok':' bad');
      });
      dlg.querySelector('#f_send2').onclick = function(){
        var u = $('#f_u2').value.trim(), em = $('#f_email2').value.trim();
        if(!u){ toast('请填写用户名'); return; }
        if(!isEmail(em)){ toast('邮箱格式不正确'); return; }
        sendEmailCode(em, 'f_send2', 'forgot');
      };
      dlg.querySelector('[data-act="forgot"]').onclick = function(){
        var username = $('#f_u2').value.trim();
        var email = $('#f_email2').value.trim();
        var code = $('#f_code2').value.trim();
        var pass = $('#f_newPw').value;
        var pass2 = $('#f_newPw2').value;
        var user = findUser(username);
        if(!user || user.email !== email){ toast('用户名与邮箱不匹配'); addLog('找回密码失败','用户名/邮箱','用户名：'+username); return; }
        if(user.status===STATUS.DELETED){ toast('账号已销户，无法重置'); return; }
        var em = emailCodeValid(email, code);
        if(em){ toast(em); return; }
        var s = passwordScore(pass);
        if(!s.ok){ toast(s.label); return; }
        if(pass !== pass2){ toast('两次密码不一致'); return; }
        user.password = pass;
        addLog('找回密码', '用户：'+username, '邮箱：'+email+'，密码重置成功');
        persistAll();
        closeAuthDialog();
        toast('密码重置成功，请使用新密码登录');
        openAuthDialog('login');
      };
    }
  }
  function showResultDialog(title, desc, pw){
    var d = document.createElement('div');
    d.className = 'dialog-mask';
    d.innerHTML =
      '<div class="dialog small">' +
        '<div class="dialog-head"><h3>'+esc(title)+'</h3><button class="dialog-close" data-x>&times;</button></div>' +
        '<div class="dialog-body">' +
          '<div class="result-card">' +
            '<div class="rc-icon">'+(window.icon?icon('check-circle',28):'√')+'</div>' +
            '<h4>'+esc(desc)+'</h4>' +
            '<p>演示模式下显示初始密码，正式环境将仅通过邮件下发</p>' +
            (pw ? '<div class="pw-box">'+esc(pw)+'</div>' : '') +
          '</div>' +
        '</div>' +
        '<div class="dialog-foot"><button class="btn-primary" data-ok>好的</button></div>' +
      '</div>';
    document.body.appendChild(d);
    d.addEventListener('click', function(e){ if(e.target===d) d.remove(); });
    d.querySelector('[data-x]').onclick = function(){ d.remove(); };
    d.querySelector('[data-ok]').onclick = function(){ d.remove(); };
  }

  /* ============================================================
   * 6. 修改密码弹窗（已登录用户）
   * ========================================================== */
  function openChangePwdDialog(){
    if(isGuest()){ toast('请先登录'); return; }
    var u = findUser(U.session.username); if(!u) return;
    var d = document.createElement('div');
    d.className = 'dialog-mask';
    d.innerHTML =
      '<div class="dialog">' +
        '<div class="dialog-head"><h3>'+(window.icon?icon('key',16)+' ':'')+'修改密码</h3><button class="dialog-close" data-x>&times;</button></div>' +
        '<div class="dialog-body">' +
          '<div class="form-grid">' +
            '<div class="fg-item full"><label>用户名（不可修改）</label><input disabled value="'+esc(u.username)+'"></div>' +
            '<div class="fg-item full"><label>原始密码 <span class="req">*</span></label><input id="cp_old" type="password"></div>' +
            '<div class="fg-item full"><label>新密码 <span class="req">*</span></label><input id="cp_new" type="password" maxlength="30">' +
              '<div class="pw-strength"><i class="pw-bar" id="cpp1"></i><i class="pw-bar" id="cpp2"></i><i class="pw-bar" id="cpp3"></i><i class="pw-bar" id="cpp4"></i></div>' +
              '<span class="pw-label" id="cpLabel">6~30 位，3 类及以上字符组合</span>' +
            '</div>' +
            '<div class="fg-item full"><label>确认新密码 <span class="req">*</span></label><input id="cp_new2" type="password" maxlength="30"><span class="fg-hint" id="cpHint"></span></div>' +
          '</div>' +
        '</div>' +
        '<div class="dialog-foot"><button class="btn-secondary" data-cancel>取消</button><button class="btn-primary" data-ok>提交修改</button></div>' +
      '</div>';
    document.body.appendChild(d);
    d.addEventListener('click', function(e){ if(e.target===d) d.remove(); });
    d.querySelector('[data-x]').onclick = function(){ d.remove(); };
    d.querySelector('[data-cancel]').onclick = function(){ d.remove(); };
    var np = d.querySelector('#cp_new'), hint=d.querySelector('#cpHint'), np2=d.querySelector('#cp_new2');
    np.addEventListener('input', function(){
      var s = paintPwStrength(this.value, 'cpp');
      var lab = document.getElementById('cpLabel'); lab.textContent = s.label;
      lab.className = 'pw-label' + (s.ok?' ok':' bad');
    });
    np2.addEventListener('input', function(){
      if(!this.value){ hint.textContent=''; return; }
      if(this.value!==np.value){ hint.textContent='两次密码不一致'; hint.className='fg-hint bad'; }
      else { hint.textContent='两次密码一致'; hint.className='fg-hint ok'; }
    });
    d.querySelector('[data-ok]').onclick = function(){
      if(u.password !== d.querySelector('#cp_old').value){ toast('原始密码错误'); return; }
      var newPw = np.value, newPw2 = np2.value;
      var s = passwordScore(newPw);
      if(!s.ok){ toast(s.label); return; }
      if(newPw !== newPw2){ toast('两次新密码不一致'); return; }
      if(newPw === u.password){ toast('新密码与原密码相同'); return; }
      u.password = newPw;
      addLog('改密','本账号','用户 '+u.username+' 自主修改密码成功');
      persistAll(); d.remove(); toast('密码已修改');
    };
    if(typeof refreshIcons === 'function') refreshIcons();
  }

  /* ============================================================
   * 7. 用户管理后台页面
   * ========================================================== */
  var qUsers = { kw:'', dept:'', status:'', page:1, pageSize:10 };

  function renderUsersPage(){
    var box = document.getElementById('usersBody');
    if(!box) return;
    var view = document.getElementById('view-users');
    if(!view) return;
    if(view.hidden) return;
    var u = isGuest() ? null : findUser(U.session.username);
    if(!u || u.role!==ROLE.SUPER){
      box.innerHTML =
        '<div class="perm-deny">' +
          '<div class="pd-icon">'+(window.icon?icon('shield-off',64):'🔒')+'</div>' +
          '<h3>无权限访问用户管理后台</h3>' +
          '<p>仅超级管理员可进入本页面进行账号管理；如需权限请联系管理员授权。</p>' +
        '</div>';
      return;
    }
    var total = U.users.length;
    var active   = U.users.filter(function(x){ return x.status===STATUS.ACTIVE; }).length;
    var disabled = U.users.filter(function(x){ return x.status===STATUS.DISABLED; }).length;
    var deleted  = U.users.filter(function(x){ return x.status===STATUS.DELETED; }).length;

    var deptOpts = '<option value="">全部部门</option>' + DEPTS.map(function(d){return '<option value="'+esc(d)+'"'+(qUsers.dept===d?' selected':'')+'>'+esc(d)+'</option>';}).join('');
    var statusOpts = '<option value="">全部状态</option>' +
                     '<option value="active"'  +(qUsers.status==='active'?' selected':'')+'>启用</option>' +
                     '<option value="disabled"'+(qUsers.status==='disabled'?' selected':'')+'>禁用</option>' +
                     '<option value="deleted"' +(qUsers.status==='deleted'?' selected':'')+'>销户</option>';

    var list = U.users.filter(function(uu){
      if(qUsers.status && uu.status!==qUsers.status) return false;
      if(qUsers.dept && uu.dept!==qUsers.dept) return false;
      if(qUsers.kw){
        var k = qUsers.kw.toLowerCase();
        if(uu.username.toLowerCase().indexOf(k)<0 && (uu.name||'').toLowerCase().indexOf(k)<0 && (uu.empNo||'').toLowerCase().indexOf(k)<0) return false;
      }
      return true;
    });
    var totalMatch = list.length;
    var pages = Math.max(1, Math.ceil(totalMatch / qUsers.pageSize));
    if(qUsers.page>pages) qUsers.page = pages;
    var start = (qUsers.page-1)*qUsers.pageSize;
    var pageList = list.slice(start, start+qUsers.pageSize);

    var rows = pageList.map(function(uu){
      var roleHtml = '<span class="role-badge '+ROLE_CLASS[uu.role]+'">'+ROLE_NAME[uu.role]+'</span>';
      var stat = uu.status;
      var statText = STATUS_NAME[stat];
      var statCls = stat===STATUS.ACTIVE?'stat-active':(stat===STATUS.DISABLED?'stat-disabled':'stat-deleted');
      var ops = [];
      if(stat !== STATUS.DELETED){
        ops.push('<button class="row-op" data-edit="'+uu.id+'">'+(window.icon?icon('edit-3',12):'')+'编辑</button>');
        if(stat===STATUS.ACTIVE)   ops.push('<button class="row-op" data-disable="'+uu.id+'">'+(window.icon?icon('ban',12):'')+'禁用</button>');
        if(stat===STATUS.DISABLED) ops.push('<button class="row-op" data-enable="'+uu.id+'">'+(window.icon?icon('check',12):'')+'启用</button>');
        ops.push('<button class="row-op" data-reset="'+uu.id+'">'+(window.icon?icon('refresh-cw',12):'')+'重置密码</button>');
        ops.push('<button class="row-op danger" data-delete="'+uu.id+'">'+(window.icon?icon('trash-2',12):'')+'销户</button>');
      } else {
        ops.push('<button class="row-op" disabled style="opacity:.5">已销户</button>');
      }
      var ch = (uu.name||uu.username).slice(0,1);
      return '<tr>' +
        '<td style="width:60px">'+(start+pageList.indexOf(uu)+1)+'</td>' +
        '<td><div class="user-mini"><span class="avatar-mini">'+esc(ch)+'</span><div><div style="font-weight:600">'+esc(uu.username)+'</div><div style="font-size:11px;color:var(--text-dim)">'+esc(uu.name)+'</div></div></div></td>' +
        '<td>'+esc(uu.dept||'—')+'</td>' +
        '<td>'+esc(uu.empNo||'—')+'</td>' +
        '<td>'+esc(uu.phone||'—')+'</td>' +
        '<td>'+esc(uu.email||'—')+'</td>' +
        '<td>'+esc(uu.person||'—')+'</td>' +
        '<td style="width:80px"><span class="'+statCls+'">'+esc(statText)+'</span></td>' +
        '<td style="width:110px">'+roleHtml+'</td>' +
        '<td style="width:160px">'+esc(uu.createdAt||'—')+'</td>' +
        '<td style="width:270px"><div class="row-ops" style="flex-wrap:wrap">'+ops.join('')+'</div></td>' +
      '</tr>';
    }).join('');

    var empty = '';
    if(!rows){
      empty = '<tr><td colspan="11" style="padding:60px 20px;text-align:center;color:var(--text-dim)">没有匹配的用户</td></tr>';
    }

    var pgHtml = '';
    if(pages>1){
      var btns = '';
      for(var i=1;i<=pages;i++){
        btns += '<button class="pg-btn'+(i===qUsers.page?' active':'')+'" data-p="'+i+'">'+i+'</button>';
      }
      pgHtml =
        '<div class="pagination">' +
          '<div>共 <b>'+totalMatch+'</b> 条符合，'+qUsers.pageSize+' 条/页，当前第 '+qUsers.page+' / '+pages+' 页</div>' +
          '<div class="pg-right">' +
            '<button class="pg-btn" data-p="'+Math.max(1,qUsers.page-1)+'"'+(qUsers.page<=1?' disabled':'')+'>上一页</button>' +
            btns +
            '<button class="pg-btn" data-p="'+Math.min(pages,qUsers.page+1)+'"'+(qUsers.page>=pages?' disabled':'')+'>下一页</button>' +
          '</div>' +
        '</div>';
    } else {
      pgHtml = '<div class="pagination"><div>共 <b>'+totalMatch+'</b> 条</div><div></div></div>';
    }

    box.innerHTML =
      '<div class="users-layout">' +
        '<div class="users-head">' +
          '<div class="uh-left">' +
            '<h2>'+(window.icon?icon('users',18)+' ':'')+'用户管理后台</h2>' +
            '<p>全平台账号生命周期管理：注册审核、角色权限、禁用启用、密码重置、永久销户，所有操作留痕审计。</p>' +
          '</div>' +
          '<div class="stats-inline">' +
            '<div class="si"><div class="si-num">'+total+'</div><div class="si-label">用户总数</div></div>' +
            '<div class="si"><div class="si-num" style="color:#2CA55F">'+active+'</div><div class="si-label">启用中</div></div>' +
            '<div class="si"><div class="si-num" style="color:#D38612">'+disabled+'</div><div class="si-label">已禁用</div></div>' +
            '<div class="si"><div class="si-num" style="color:var(--text-dim)">'+deleted+'</div><div class="si-label">已销户</div></div>' +
          '</div>' +
        '</div>' +
        '<div class="ability-card" style="padding:16px 18px 10px">' +
          '<div class="query-form" style="padding:0">' +
            '<div class="qf-item"><label>关键词</label><input id="q_kw" style="width:220px" placeholder="用户名/姓名/工号" value="'+esc(qUsers.kw)+'"></div>' +
            '<div class="qf-item"><label>部门</label><select id="q_dept" style="width:190px">'+deptOpts+'</select></div>' +
            '<div class="qf-item"><label>状态</label><select id="q_status" style="width:160px">'+statusOpts+'</select></div>' +
            '<div class="qf-ops">' +
              '<button class="btn-secondary" id="q_reset">'+(window.icon?icon('rotate-ccw',13):'')+'重置</button>' +
              '<button class="btn-primary" id="q_go">'+(window.icon?icon('search',13):'')+'查询</button>' +
              '<button class="btn-primary" id="q_new" style="margin-left:8px">'+(window.icon?icon('user-plus',13):'')+'新增用户</button>' +
            '</div>' +
          '</div>' +
        '</div>' +
        '<div class="usertable-wrap">' +
          '<div class="usertable-scroll" tabindex="0">' +
            '<table class="usertable"><colgroup>' +
              '<col style="width:60px"><col style="width:200px"><col style="width:120px"><col style="width:100px"><col style="width:130px"><col style="width:200px"><col style="width:90px"><col style="width:80px"><col style="width:110px"><col style="width:160px"><col style="width:270px">' +
            '</colgroup><thead><tr>' +
              '<th>序号</th><th>账号 / 姓名</th><th>所属部门</th><th>工号</th><th>手机号</th><th>邮箱</th><th>人员性质</th><th>状态</th><th>角色</th><th>创建时间</th><th>操作</th>' +
            '</tr></thead><tbody>' + (rows||empty) + '</tbody></table>' +
          '</div>' +
          pgHtml +
        '</div>' +
      '</div>';

    var qGo = function(){
      qUsers.kw = (document.getElementById('q_kw').value||'').trim();
      qUsers.dept = document.getElementById('q_dept').value;
      qUsers.status = document.getElementById('q_status').value;
      qUsers.page = 1;
      renderUsersPage();
    };
    document.getElementById('q_go').onclick = qGo;
    document.getElementById('q_reset').onclick = function(){
      qUsers = { kw:'', dept:'', status:'', page:1, pageSize:10 };
      renderUsersPage();
    };
    document.getElementById('q_new').onclick = function(){ openUserEditDialog(null); };
    document.getElementById('q_kw').addEventListener('keydown', function(e){ if(e.key==='Enter') qGo(); });
    $$('.pg-btn[data-p]', box).forEach(function(b){
      b.onclick = function(){ qUsers.page = Number(b.dataset.p); renderUsersPage(); };
    });
    $$('[data-edit]', box).forEach(function(b){ b.onclick = function(){ openUserEditDialog(b.dataset.edit); }; });
    $$('[data-disable]', box).forEach(function(b){ b.onclick = function(){ openReasonDialog('disable', b.dataset.disable); }; });
    $$('[data-enable]', box).forEach(function(b){  b.onclick = function(){ openReasonDialog('enable',  b.dataset.enable); }; });
    $$('[data-reset]', box).forEach(function(b){   b.onclick = function(){ doResetPassword(b.dataset.reset); }; });
    $$('[data-delete]', box).forEach(function(b){  b.onclick = function(){ openReasonDialog('delete', b.dataset.delete); }; });
    if(typeof refreshIcons === 'function') refreshIcons();
  }

  /* 新增/编辑用户 */
  function openUserEditDialog(id){
    var isNew = !id;
    var u = isNew ? { id:randId('u'), username:'', password:'', name:'', dept:DEPTS[0], empNo:'', phone:'', email:'', person:'公司职工', role:ROLE.NORMAL, status:STATUS.ACTIVE, createdAt:fmtNow(), deletedReserved:false }
                  : U.users.find(function(x){ return x.id===id; });
    if(!u) return;
    if(u.status===STATUS.DELETED){ toast('已销户账号不可编辑'); return; }
    var deptOpts = DEPTS.map(function(d){ return '<option value="'+esc(d)+'"'+(u.dept===d?' selected':'')+'>'+esc(d)+'</option>'; }).join('');
    var personOpts = PERSON_TYPE.map(function(p){ return '<option value="'+esc(p)+'"'+(u.person===p?' selected':'')+'>'+esc(p)+'</option>'; }).join('');
    var roleOpts = [
      {k:ROLE.NORMAL, n:'普通用户'},
      {k:ROLE.CONFIG, n:'配置管理员'},
      {k:ROLE.SUPER,  n:'超级管理员'}
    ].map(function(r){ return '<option value="'+r.k+'"'+(u.role===r.k?' selected':'')+'>'+r.n+'</option>'; }).join('');

    var d = document.createElement('div');
    d.className = 'dialog-mask';
    d.innerHTML =
      '<div class="dialog large">' +
        '<div class="dialog-head"><h3>'+(window.icon?icon((isNew?'user-plus':'edit-3'),16)+' ':'')+(isNew?'新增用户':'编辑用户')+'</h3><button class="dialog-close" data-x>&times;</button></div>' +
        '<div class="dialog-body">' +
          '<div class="form-grid">' +
            '<div class="fg-item"><label>用户名 <span class="req">*</span> <span class="fg-hint">字母数字，3~24 位，全局唯一</span></label>' +
              '<input id="eu_u" maxlength="24" value="'+esc(u.username)+'"'+(isNew?'':' disabled')+'>' +
              
            '</div>' +
            '<div class="fg-item"><label>真实姓名 <span class="req">*</span></label><input id="eu_name" maxlength="20" value="'+esc(u.name)+'"></div>' +
            '<div class="fg-item"><label>所属部门 <span class="req">*</span></label><select id="eu_dept">'+deptOpts+'</select></div>' +
            '<div class="fg-item"><label>员工工号</label><input id="eu_emp" maxlength="16" value="'+esc(u.empNo)+'"><span class="fg-hint">公司职工必填，外包/其他可空</span></div>' +
            '<div class="fg-item"><label>人员性质</label><select id="eu_person">'+personOpts+'</select></div>' +
            '<div class="fg-item"><label>人员角色 <span class="req">*</span></label><select id="eu_role">'+roleOpts+'</select></div>' +
            '<div class="fg-item"><label>手机号码 <span class="req">*</span></label><input id="eu_phone" maxlength="11" value="'+esc(u.phone)+'"></div>' +
            '<div class="fg-item full"><label>邮箱地址 <span class="req">*</span><span class="fg-hint">全局唯一</span></label><input id="eu_email" value="'+esc(u.email)+'"></div>' +
          '</div>' +
        '</div>' +
        '<div class="dialog-foot"><button class="btn-secondary" data-cancel>取消</button><button class="btn-primary" data-ok>'+(window.icon?icon('check',14)+' ':'')+(isNew?'创建账号':'保存修改')+'</button></div>' +
      '</div>';
    document.body.appendChild(d);
    d.addEventListener('click', function(e){ if(e.target===d) d.remove(); });
    d.querySelector('[data-x]').onclick = function(){ d.remove(); };
    d.querySelector('[data-cancel]').onclick = function(){ d.remove(); };
    d.querySelector('[data-ok]').onclick = function(){
      var username = d.querySelector('#eu_u').value.trim();
      var name = d.querySelector('#eu_name').value.trim();
      var dept = d.querySelector('#eu_dept').value;
      var emp = d.querySelector('#eu_emp').value.trim();
      var person = d.querySelector('#eu_person').value;
      var role = d.querySelector('#eu_role').value;
      var phone = d.querySelector('#eu_phone').value.trim();
      var email = d.querySelector('#eu_email').value.trim();
      if(isNew && !isUsername(username)){ toast('用户名仅允许字母数字，3~24 位'); return; }
      if(isNew && usernameTaken(username)){ toast('用户名已被占用'); return; }
      if(u.deletedReserved && usernameTaken(username) && isNew){ toast('该用户名已被销户账号永久占用'); return; }
      if(!name){ toast('请填写真实姓名'); return; }
      if(person==='公司职工' && !emp){ toast('公司职工必须填写工号'); return; }
      if(!isPhone(phone)){ toast('请输入正确的手机号码'); return; }
      if(!isEmail(email)){ toast('请输入正确的邮箱地址'); return; }
      if(emailTaken(email, u.id)){ toast('邮箱已被其他账号占用'); return; }
      u.name=name; u.dept=dept; u.empNo=emp; u.person=person; u.role=role; u.phone=phone;
      if(isNew){ u.username=username; u.email=email; u.status=STATUS.ACTIVE; u.createdAt=fmtNow();
        var initPw = genRandomPw(); u.password = initPw;
        U.users.unshift(u);
        addLog('新增', '用户：'+username, '角色：'+ROLE_NAME[role]+'，初始密码已发送邮箱（演示：'+initPw+'）');
        persistAll(); d.remove(); renderUsersPage();
        showResultDialog('账号创建成功', '初始密码已下发至 '+email, initPw);
      } else {
        var emailChanged = u.email !== email;
        u.email = email;
        addLog('编辑', '用户：'+u.username, '用户资料已更新'+(emailChanged?'，邮箱已变更，系统已向新邮箱发送验证码（演示自动通过）':''));
        persistAll(); d.remove(); renderUsersPage(); toast('保存成功');
      }
    };
    if(typeof refreshIcons === 'function') refreshIcons();
  }

  /* 禁用/启用/销户 操作 —— 必须填写说明 */
  function openReasonDialog(kind, id){
    var u = findUserById(id); if(!u) return;
    if(u.status===STATUS.DELETED){ toast('已销户账号不可操作'); return; }
    var label = kind==='disable'?'禁用账号':(kind==='enable'?'启用账号':'永久销户');
    var tip = kind==='disable' ? '禁用后该用户将无法登录，历史操作日志保留。'
              : kind==='enable' ? '启用后该用户恢复登录与使用权限。'
              : '<b style="color:#E64448">高危操作：</b>销户后账号永久失效，用户名将被永久占用不可复用，历史日志保留不可删除。';
    var d = document.createElement('div');
    d.className = 'dialog-mask';
    d.innerHTML =
      '<div class="dialog">' +
        '<div class="dialog-head"><h3>'+(window.icon?icon(kind==='delete'?'alert-triangle':'edit-3',16)+' ':'')+label+'：'+esc(u.username)+'</h3><button class="dialog-close" data-x>&times;</button></div>' +
        '<div class="dialog-body">' +
          '<div class="desc-list">' +
            '<div class="dl-row"><div class="dl-label">用户名</div><div class="dl-value">'+esc(u.username)+'（'+esc(u.name)+'）</div></div>' +
            '<div class="dl-row"><div class="dl-label">当前状态</div><div class="dl-value">'+esc(STATUS_NAME[u.status])+'</div></div>' +
            '<div class="dl-row"><div class="dl-label">部门 / 角色</div><div class="dl-value">'+esc(u.dept||'—')+' / '+ROLE_NAME[u.role]+'</div></div>' +
          '</div>' +
          '<div style="margin-top:16px"><label style="font-size:13px;color:var(--text)"><span class="req" style="color:#E64448">*</span> '+label+'说明 <span style="color:var(--text-dim);font-weight:400;margin-left:6px;font-size:12px">（必填，10~200 字，将记录进审计日志）</span></label>' +
          '<textarea id="rs_reason" class="reason-area" maxlength="200" placeholder="请详细说明'+label+'原因，便于审计追溯"></textarea>' +
          '</div>' +
          '<div class="log-extra" style="margin-top:10px">' +
            (window.icon?icon('info',12)+' ':'') + '<b>操作须知</b>：'+tip +
          '</div>' +
        '</div>' +
        '<div class="dialog-foot"><button class="btn-secondary" data-cancel>取消</button><button class="btn-primary '+(kind==='delete'?'':'')+'" data-ok>'+(window.icon?icon('check',14)+' ':'')+'确认'+label+'</button></div>' +
      '</div>';
    document.body.appendChild(d);
    d.addEventListener('click', function(e){ if(e.target===d) d.remove(); });
    d.querySelector('[data-x]').onclick = function(){ d.remove(); };
    d.querySelector('[data-cancel]').onclick = function(){ d.remove(); };
    d.querySelector('[data-ok]').onclick = function(){
      var reason = (d.querySelector('#rs_reason').value||'').trim();
      if(reason.length<10){ toast('操作说明不能少于 10 个字'); return; }
      if(kind==='delete'){
        u.status = STATUS.DELETED; u.deletedReserved = true; u.deleteReason = reason; u.password = '';
        addLog('销户', '用户：'+u.username, '销户原因：'+reason);
      } else if(kind==='disable'){
        u.status = STATUS.DISABLED; u.disableReason = reason;
        addLog('禁用', '用户：'+u.username, '禁用原因：'+reason);
      } else if(kind==='enable'){
        u.status = STATUS.ACTIVE; u.enableReason = reason;
        addLog('启用', '用户：'+u.username, '启用原因：'+reason);
      }
      persistAll(); d.remove(); renderUsersPage(); toast('操作已生效');
    };
    if(typeof refreshIcons === 'function') refreshIcons();
  }

  /* 重置密码 */
  function doResetPassword(id){
    var u = findUserById(id); if(!u) return;
    if(u.status===STATUS.DELETED){ toast('已销户账号不可重置密码'); return; }
    var d = document.createElement('div');
    d.className = 'dialog-mask';
    d.innerHTML =
      '<div class="dialog small">' +
        '<div class="dialog-head"><h3>'+(window.icon?icon('key',16)+' ':'')+'重置密码：'+esc(u.username)+'</h3><button class="dialog-close" data-x>&times;</button></div>' +
        '<div class="dialog-body">' +
          '<div class="desc-list">' +
            '<div class="dl-row"><div class="dl-label">姓名</div><div class="dl-value">'+esc(u.name)+'</div></div>' +
            '<div class="dl-row"><div class="dl-label">邮箱</div><div class="dl-value">'+esc(u.email)+'</div></div>' +
          '</div>' +
          '<div class="log-extra" style="margin-top:12px">将自动生成符合密码策略的新密码并下发至上述邮箱。演示模式下弹窗展示新密码，正式环境仅通过邮件下发。</div>' +
        '</div>' +
        '<div class="dialog-foot"><button class="btn-secondary" data-cancel>取消</button><button class="btn-primary" data-ok>'+(window.icon?icon('check',14)+' ':'')+'确认重置</button></div>' +
      '</div>';
    document.body.appendChild(d);
    d.addEventListener('click', function(e){ if(e.target===d) d.remove(); });
    d.querySelector('[data-x]').onclick = function(){ d.remove(); };
    d.querySelector('[data-cancel]').onclick = function(){ d.remove(); };
    d.querySelector('[data-ok]').onclick = function(){
      var newPw = genRandomPw();
      u.password = newPw;
      addLog('重置密码','用户：'+u.username,'系统重置，新密码已发送至 '+u.email+'（演示：'+newPw+'）');
      persistAll(); d.remove(); renderUsersPage();
      showResultDialog('密码已重置', '新密码已下发至 '+u.email, newPw);
    };
    if(typeof refreshIcons === 'function') refreshIcons();
  }

  /* ============================================================
   * 8. 对外暴露 API + 初始化
   * ========================================================== */
  window.Auth = {
    isGuest: isGuest,
    currentUser: function(){ return isGuest()?null:(findUser(U.session.username)||null); },
    hasPerm: hasPerm,
    renderSlot: renderUserSlot,
    renderPage: renderUsersPage,
    renderGuestBanner: renderGuestBanner,
    openLogin: function(){ openAuthDialog('login'); },
    logout: doLogout,
    allLogs: function(){ return U.logs; },
    allUsers: function(){ return U.users; }
  };

  /* 辅助：esc、icon、$、$$、toast、refreshIcons 由其它文件提供，做兜底 */
  function esc(s){ if(s==null) return ''; return String(s).replace(/[&<>"']/g, function(m){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m];}); }
  function $(sel, ctx){ return (ctx||document).querySelector(sel); }
  function $$(sel, ctx){ return Array.prototype.slice.call((ctx||document).querySelectorAll(sel)); }
  function icon(k, sz){ return '<span data-icon="'+k+'"'+(sz?' data-size="'+sz+'"':'')+'></span>'; }
  var _toastTimer = null;
  function toast(msg){
    var el = document.getElementById('toast');
    if(!el){
      el = document.createElement('div'); el.id='toast'; el.className='toast'; el.setAttribute('role','status');
      document.body.appendChild(el);
    }
    el.textContent = msg; el.classList.add('show');
    clearTimeout(_toastTimer);
    _toastTimer = setTimeout(function(){ el.classList.remove('show'); }, 2400);
  }
  window.esc = window.esc || esc;
  window.icon = window.icon || icon;
  window.$ = window.$ || $;
  window.$$ = window.$$ || $$;
  window.toast = window.toast || toast;

  /* DOM 就绪后初始化 */
  function initUserModule(){
    /* 强制清除遗留的演示态会话，保证首次打开必须走登录验证（admin / Cap2026） */
    var raw = null;
    try { raw = localStorage.getItem(STORAGE_KEY); } catch(e){}
    var persisted = null;
    try { if(raw) persisted = JSON.parse(raw); } catch(e){ persisted = null; }
    if(persisted && persisted.isDefault){
      try{ localStorage.removeItem(STORAGE_KEY); }catch(e){}
      U.session = null;
    }
    renderUserSlot();
    renderGuestBanner();
    /* 若直接访问 #ability / #users 等受限页，先弹出登录框 */
    var h = (location.hash||'#apps').replace('#','');
    if(h==='ability' || h==='users'){
      setTimeout(function(){ openAuthDialog('login'); toast('请先以管理员身份登录再使用后台'); }, 120);
    }
    /* 监听 hash 变化，#users 触发渲染（app.js 中 handleHash 已处理 hidden 切换） */
    window.addEventListener('hashchange', function(){
      renderUserSlot();
      renderUsersPage();
      var h2 = (location.hash||'#apps').replace('#','');
      if((h2==='ability'||h2==='users') && !window.Auth.hasPerm(h2==='users'?'super':'config')){
        setTimeout(function(){ openAuthDialog('login'); }, 80);
      }
    });
    renderUsersPage();
  }
  if(document.readyState==='loading'){
    document.addEventListener('DOMContentLoaded', initUserModule);
  } else { initUserModule(); }

  /* 给 app.js 暴露的视图路由钩子：若 app.js handleHash 未识别 #users，此处兼容切换 */
  var _origHashSync = null;
  function syncViewHiddenByHash(){
    var h = (location.hash||'#apps').replace('#','');
    ['apps','stats','ability','users'].forEach(function(v){
      var el = document.getElementById('view-'+v);
      if(!el) return;
      el.hidden = (v !== (['apps','stats','ability','users'].indexOf(h)>=0?h:'apps'));
    });
    var navs = $$('.main-nav a[data-nav]');
    navs.forEach(function(a){
      a.classList.toggle('active', a.dataset.nav === (['apps','stats','ability','users'].indexOf(h)>=0?h:'apps'));
    });
  }
  window.addEventListener('hashchange', syncViewHiddenByHash);
  if(document.body) syncViewHiddenByHash();

})();
