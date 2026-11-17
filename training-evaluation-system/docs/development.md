# 开发与接口

## 约定

Java 8 语法和 API，UTF-8；禁止手改已执行迁移，新变更新增 V4 等脚本。生产与应用时区统一 Asia/Shanghai。前端无 Node 构建，跟随 JAR 发布；业务数据使用文本节点渲染，不使用 HTML 拼接。

Controller 处理协议，TrainingService 检查角色和数据范围；任何新业务接口均需显式调用权限检查。事务服务注入后通过方法访问，不直接访问代理对象字段。写操作与业务审计应在同一事务。

## API

手机号账号新增接口：GET `/api/auth/captcha` 返回PNG，POST `/api/auth/register`（phone/display_name/password/role/captcha，仅STUDENT或TEACHER）；GET `/api/auth/verification-options` 返回验证能力；GET `/api/portal/courses` 获取个人课程；POST `/api/portal/evaluate` 由后端从登录学员取得资格；GET `/api/business/accounts/template` 与POST `/api/business/accounts/import` 为管理员账号批量导入。均沿用CSRF和会话规则。手机号字段由数据库唯一索引兜底，自注册不接受档案编号。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | /api/auth/csrf | CSRF 请求头名与 token |
| POST | /api/auth/login | 表单 username/password |
| POST | /api/auth/logout | 退出 |
| GET | /api/auth/me | 数据库账号与角色（不返回密码） |
| POST | /api/auth/password | old_password/new_password，成功后重新登录 |
| GET / POST | /api/business/{entity} | 查询 / 创建 |
| PUT / DELETE | /api/business/{entity}/{id} | 修改 / 删除 |
| GET | /api/business/classes/{id}/members | 班次名单 |
| POST / DELETE | /api/business/classes/{id}/members/{student} | 关联 / 移除学员 |
| POST | /api/business/students/import | multipart xlsx 文件，字段 file |
| POST | /api/business/tasks/{id}/status | status=CLOSED / REVOKED |
| GET | /api/business/tasks/{id}/codes | 个人评价码及提交状态 |
| GET | /api/business/tasks/{id}/codes/excel | 个人码 Excel |
| GET | /api/business/tasks/{id}/qr | PNG 二维码 |
| GET | /api/business/tasks/{id}/report | 任务汇总及匿名展示的评分明细 |
| GET | /api/business/tasks/{id}/excel | 答卷明细 Excel |
| GET | /api/business/tasks/{id}/pdf | 纸质评价 PDF |
| GET | /api/business/statistics?group=lesson/class/teacher | 统计汇总 |
| GET | /api/business/statistics/excel?group=lesson/class/teacher | 统计 Excel |
| POST | /api/business/paper | 纸质补录，额外提供 paper_reference |
| GET | /api/public/task?token=... | 课程及模板，不返回个人码 |
| POST | /api/public/submit | 线上评分 |
| GET | /api/admin/audit | 最近100条审计 |

entity 白名单：users、classes、teachers、students、lessons、templates、tasks。用户使用停用代替删除；任务需关闭/撤销后才可调用DELETE，采取保留历史评分和审计的逻辑删除。相同授课的未删除任务阻止再次发布。字段可参阅前端 modules 配置与数据库迁移。

提交字段：token、access_code、teaching、content、organization、comment；评分仅允许1—5整数，意见至多1000字。纸质补录额外提供80字内纸表编号。前端评分标题取模板，后端字段名稳定。

成功格式 `{"code":"OK","message":"成功","data":...}`。参数/业务错误400，未登录401，越权或 CSRF 失败403，数据引用冲突及并发重试409，未知服务异常500；不向用户返回 SQL 和堆栈。

写请求先获取 CSRF token，再携带声明的请求头和同源 Cookie。登录后 token 重新获取。密码不写 localStorage，不导出密码摘要。

## 测试

`mvn test` 使用独立 H2。测试覆盖权限范围、真实登录、停用/角色变更/改密、评分时限、并发防重复、跨渠道重复、发布名单快照、模板不可改写及权重、导出文件、导入原子性、最后管理员保护和路由。H2 测试通过后仍须做真实 MySQL 迁移与业务联调。

可选安装 Node 后执行 `node scripts/frontend-smoke.cjs`，无 npm 依赖，仅测试页面脚本逻辑，不替代浏览器验收。

真实本机数据库验证需显式设置 `RUN_MYSQL_SMOKE=true`、`DB_USERNAME`、`DB_PASSWORD`，再执行 `mvn -Dtest=MySqlSmokeTest test`。该测试会保留启动迁移和初始管理员，业务验证数据在测试事务内回滚；仅在开发库运行。
