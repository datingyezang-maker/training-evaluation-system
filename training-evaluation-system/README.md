# 培训评估系统

Java 1.8 / Spring Boot 2.7.18 单体应用，PC 后台、独立 H5 和 API 同一个 JAR 交付。数据库为本机 MySQL `127.0.0.1:3307/training_evaluation`，兼容当前 MySQL 5.7。

## 已实现

新增手机号账号：学员/教师入口 `/account/`，PC与移动端注册、图形验证码、教师审核、后台赋权及账号Excel导入。手机号唯一，人员账号只访问自己的课程，详见下方手机号账号文档。

- 数据库账号、BCrypt 密码、角色权限、班次数据范围、修改密码、停用、失败登录锁定及旧会话失效。
- 班次、教师、学员、课程授课记录的新增、编辑、查询、删除；外键保护历史引用。
- 学员 Excel 导入（整批校验、错误回滚）、班次学员关联及移除。
- 数据库评分模板：可增删、排序的1—30项维度及权重；使用后锁定版本。
- 评价任务发布、应评名单快照、课程二维码、个人评价码查看与 Excel 导出、关闭与撤销。
- 手机 H5：扫码读取课程、个人评价码核验、1—5分评分、意见、成功回执；事务防重复。
- 任务明细、班次/授课/教师统计、参与率、加权均分、Excel 导出。
- 中文 PDF 纸质评价表、纸表编号与人工补录、线上纸质去重。
- 数据库迁移、基础访问审计和业务操作事务审计。

所有业务记录、用户、角色分配、评分模板和审计保存在数据库；会话和运行配置仍为运行态，不当作业务数据持久化。未调用外部问卷、二维码、AI 或统计服务。

## 本地启动

安装 JDK 1.8、Maven，确认 `java -version`、`mvn -version` 都使用 Java 8。先启动 MySQL，确保数据库已存在。

```powershell
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = '你的本机数据库密码'
mvn spring-boot:run
```

IDEA：运行 `EvaluationApplication`，在“运行 → 编辑配置 → 环境变量”中设置 `DB_USERNAME`、`DB_PASSWORD`。在 PowerShell 临时设置的变量不会传给已打开的 IDEA。

后台：http://localhost:8080/admin/ 。**首次初始化账号 `admin`，开发初始密码 `Training@2026`**。首次启动可通过 `APP_ADMIN_PASSWORD` 替换初始密码。初始化后以数据库密码为准，修改环境变量不会重置账号，请在后台修改密码。连续失败5次锁定15分钟。

```powershell
mvn test
mvn clean package
java -jar target/training-evaluation-0.1.0-SNAPSHOT.jar
```

自动化测试使用独立内存 H2，不写本地业务库。首次启动 Flyway 自动创建/升级业务表，不自动创建数据库。

## 操作顺序

1. 登录后先修改密码；在“用户权限”创建业务人员和只读账号。
2. 录入教师、学员（可 Excel 导入）、班次，设置班次负责人并关联学员。
3. 录入授课安排，选择班次与教师；配置评分模板。
4. 发布评价任务，选择授课、模板及开放/截止时间。
5. 查看并逐人发放个人评价码，再投屏/打印公共课程二维码。
6. 学员扫码评分；后台查看进度、结果与导出，必要时使用 PDF 和纸质补录。

## 手机扫码配置

当前 dev 配置临时使用 `https://graeme-brinish-granville.ngrok-free.dev` 生成二维码。需保持 `ngrok http 8080` 运行；地址变化后更新 `PUBLIC_BASE_URL` 并重启应用、重新下载二维码。该地址仅用于临时联调，不是正式私有云部署地址。

默认服务仅绑定 `127.0.0.1`，默认二维码包含 localhost，仅供电脑本机测试。手机使用微信扫一扫时，需配置手机可访问的地址：

```powershell
# 开发联调示例；地址替换为你的电脑局域网IP，手机连接同一可达网络。
$env:PUBLIC_BASE_URL = 'http://192.168.1.100:8080'
mvn spring-boot:run '-Dspring-boot.run.arguments=--server.address=0.0.0.0'
```

同时放行单位允许的防火墙规则。正式私有云部署使用单位 HTTPS 域名和有效证书，不能把手机自身的 localhost 当作服务器地址。

## 文档与边界

- [架构与权限模型](docs/architecture.md)
- [开发及接口说明](docs/development.md)
- [部署、备份与版本说明](docs/deployment.md)
- [验收清单与扩展边界](docs/roadmap.md)
- [验证记录与已修复问题](docs/verification.md)
- [管理端体验更新](docs/usability-update.md)
- [开源调研与工作流完善](docs/open-source-review.md)
- [手机号账号、注册与验证码](docs/phone-accounts.md)
- [账号审核、解锁和密码重置](docs/account-administration.md)
- [自定义评分维度与历史迁移](docs/dynamic-dimensions.md)
- [自有工作台界面](docs/workspace-ui.md)
- [界面及交互整体升级](docs/interface-redesign.md)
- [资源检查及第三方组件边界](docs/resource-audit.md)
- [核心需求逐项核对与验收边界](docs/requirements-check.md)
- [任务重复发布与删除规则](docs/task-lifecycle.md)
- [班次学员可视化选择](docs/student-picker.md)
- [学员与教师后台功能授权](docs/profile-permissions.md)
- [响应速度优化及验证](docs/performance.md)
- [学号自动生成规则](docs/student-numbers.md)
- [页面切换与操作文案一致性](docs/page-consistency.md)
- [页面及交互全面排查记录](docs/interaction-review.md)
- [管理员、教师与学员工作空间升级](docs/role-workspaces.md)

当前模板支持1—30项可增删维度、各1—5分，可配置名称与权重；不是任意题型问卷设计器。角色为 ADMIN / BUSINESS / VIEWER / STUDENT / TEACHER，支持账号角色分配与班次/个人范围；没有自定义角色设计器。纸质表采用人工补录，无 OCR。学员身份与资格存在关联，属于受控评价，不承诺完全匿名。当前只启用图形验证码，尚未验证手机号归属，不发送短信。
