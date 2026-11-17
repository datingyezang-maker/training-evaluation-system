# 私有云部署与运维

## 运行变量

| 变量 | 含义 |
| --- | --- |
| SPRING_PROFILES_ACTIVE | 生产设为 prod |
| APP_ADMIN_PASSWORD | 首次数据库初始化密码，至少12位、UTF-8不超过72字节；dev 缺省为 Training@2026 |
| DB_URL | 生产必填；dev 默认 jdbc:mysql://127.0.0.1:3307/training_evaluation?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai |
| DB_USERNAME / DB_PASSWORD | 生产账号和密码；dev 用户默认root，密码必须设置 |
| PUBLIC_BASE_URL | 手机可以访问的系统根地址，生产为单位 HTTPS 域名 |
| SERVER_PORT | 默认8080 |
| SERVER_ADDRESS | 生产默认0.0.0.0；dev默认127.0.0.1，手机联调可用启动参数覆盖 |

数据库账号和后台管理员是两套账号；修改 APP_ADMIN_PASSWORD 不会重置已入库的管理员密码。账号失锁15分钟后可重试正确密码。切勿把真实数据库密码放入仓库。

## 构建与部署

JDK8 下执行 `mvn clean package`。运行 `java -jar target/training-evaluation-0.1.0-SNAPSHOT.jar --spring.profiles.active=prod`，注入上述变量。运行包不依赖外部静态服务；构建依赖建议通过单位内部制品库提供。

生产必须 HTTPS，Cookie Secure=true。网关同源代理页面和API，管理入口 `/admin/`、`/api/admin/`、`/api/business/` 仅开放给授权网段。人员端需开放 `/account/`、`/h5/`、静态资源、公开评价API及 `/api/portal/`；共用的 `/api/auth/` 登录、注册、验证码、会话与改密接口须可达，不能整体封禁后导致学员无法登录，账号角色由服务端校验。如果单位要求管理员登录也必须限制来源，需要进一步拆分管理认证入口或接入单位SSO策略。数据库端口仅向应用开放，不单独暴露应用端口。

Flyway 首次自动建表；升级前备份。数据库已有业务表但无迁移历史时，不要随意开启 baseline，应先核对表结构。当前迁移包含外键，不会自动删用户业务记录。

## 版本与中文 PDF

Java8 约束下使用 Spring Boot 2.7.18、Flyway 7.15.0（兼容本机 MySQL5.7）、H2 1.4.200（仅测试）、OpenPDF 1.3.30。均为受兼容约束的旧基线，投产前按单位要求评估漏洞、补丁与维护来源，不能宣称已经过安全认证。

PDF 使用 STSong CJK 字体映射，字体未嵌入；打印终端需有支持 CJK 的 PDF 阅读器。要求归档级内嵌字体时，应引入单位许可的中文字体文件并改为嵌入，核验打印效果。

## 备份与异常处置

- 备份整个 MySQL 数据库，包含账号、任务、资格、答卷、模板和审计；备份留在单位内部并控制权限。
- 明确保留期限、备份频率、可接受丢失量和恢复时间，做实际恢复演练。
- 监控数据库连接、磁盘、500响应、审计写失败；代理层配置请求限流、超时及上传限制。
- 导入上限2MB/5000行；代理上传上限需配合。不要直接开放数据库给学员。
- 多实例前需增加共享 Session、迁移执行协调。当前单实例，重启会话失效，但业务记录不丢失。
- 数据库与应用时区统一 Asia/Shanghai；设置 JDBC TLS 按单位环境执行。
- 密钥和密码使用单位凭据管理，不和业务备份放在同一无权限目录。
