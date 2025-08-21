# 🚀 部署到 GitHub Packages 指南

## 📋 准备工作

### 1. 更新 pom.xml 中的用户名

将 `pom.xml` 中的 `YOUR_USERNAME` 替换为你的实际 GitHub 用户名：

```xml
<!-- 需要替换的地方 -->
<url>https://github.com/YOUR_USERNAME/smart-rate-limiter</url>

<scm>
    <connection>scm:git:git://github.com/YOUR_USERNAME/smart-rate-limiter.git</connection>
    <developerConnection>scm:git:ssh://github.com:YOUR_USERNAME/smart-rate-limiter.git</developerConnection>
    <url>https://github.com/YOUR_USERNAME/smart-rate-limiter/tree/master</url>
</scm>

<distributionManagement>
    <repository>
        <id>github</id>
        <name>GitHub Packages</name>
        <url>https://maven.pkg.github.com/YOUR_USERNAME/smart-rate-limiter</url>
    </repository>
</distributionManagement>
```

### 2. 推送代码到 GitHub

```bash
# 添加 GitHub 远程仓库
git remote add github https://github.com/YOUR_USERNAME/smart-rate-limiter.git

# 推送代码
git push github master

# 推送 GitHub Actions 工作流
git add .github/
git commit -m "Add GitHub Actions workflow for publishing"
git push github master
```

## 🎯 自动发布（推荐）

### 使用 Git 标签触发发布

```bash
# 创建并推送版本标签
git tag v1.0.0
git push github v1.0.0

# GitHub Actions 会自动：
# 1. 构建项目
# 2. 运行测试
# 3. 发布到 GitHub Packages
```

### 手动触发发布

1. 进入 GitHub 仓库
2. 点击 `Actions` 选项卡
3. 选择 `Publish to GitHub Packages` 工作流
4. 点击 `Run workflow`

## 🔧 本地发布（可选）

如果需要从本地发布，按以下步骤：

### 1. 生成 GitHub Personal Access Token

1. 进入 GitHub Settings > Developer settings > Personal access tokens > Tokens (classic)
2. 点击 "Generate new token (classic)"
3. 勾选以下权限：
   - `write:packages` - 发布包
   - `read:packages` - 读取包
   - `repo` - 仓库访问权限

### 2. 配置 Maven Settings

```bash
# 复制模板文件
cp settings.xml.template ~/.m2/settings.xml

# 编辑 ~/.m2/settings.xml，替换：
# YOUR_GITHUB_USERNAME -> 你的GitHub用户名
# YOUR_GITHUB_TOKEN -> 上面生成的Token
```

### 3. 本地发布

```bash
# 清理并发布
mvn clean deploy
```

## 📦 使用发布的包

其他项目可以这样使用你发布的包：

### 1. 在 pom.xml 中添加仓库

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/YOUR_USERNAME/smart-rate-limiter</url>
    </repository>
</repositories>
```

### 2. 添加依赖

```xml
<dependency>
    <groupId>io.github</groupId>
    <artifactId>smart-rate-limiter-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 3. 配置认证（如果仓库是私有的）

在使用者的 `~/.m2/settings.xml` 中添加：

```xml
<servers>
    <server>
        <id>github</id>
        <username>GITHUB_USERNAME</username>
        <password>GITHUB_TOKEN</password>
    </server>
</servers>
```

## 🎉 发布成功后

发布成功后，你可以在以下位置看到你的包：

- **GitHub Packages**: `https://github.com/YOUR_USERNAME/smart-rate-limiter/packages`
- **仓库页面**: 右侧会显示 "Packages" 部分

## 🔄 版本管理

建议的版本发布流程：

```bash
# 开发版本
git commit -m "feat: add new feature"
git push github master

# 发布版本
git tag v1.0.1
git push github v1.0.1    # 自动触发发布

# 发布 SNAPSHOT 版本（开发版本）
# 将 pom.xml 中的版本改为 1.0.1-SNAPSHOT
# 然后手动运行 GitHub Actions
```

## ⚠️ 注意事项

1. **包可见性**: GitHub Packages 默认是公开的，但需要认证才能下载
2. **存储限制**: GitHub 提供每月 1GB 的免费存储空间
3. **版本策略**: 建议使用语义化版本 (Semantic Versioning)
4. **分支保护**: 建议开启 master 分支保护，通过 PR 合并代码

## 🐛 故障排除

### 发布失败常见原因：

1. **认证失败**: 检查 GitHub Token 权限
2. **包名冲突**: 确保 groupId 和 artifactId 唯一
3. **版本冲突**: 不能重复发布相同版本号
4. **网络问题**: 检查网络连接

### 查看发布日志：

进入 GitHub Actions 可以查看详细的构建和发布日志。