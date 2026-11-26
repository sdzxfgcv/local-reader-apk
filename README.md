# 本地阅读 LocalReader

纯本地、离线的 Android 小说/电子书阅读器。源码在 GitHub，APK 由 GitHub Actions 云构建。

## 功能
- 从手机文件管理器导入 TXT / EPUB，书籍复制到 App 私有目录，不联网、不上传
- 书架：按最近阅读排序，长按删除
- TXT 自动识别 UTF-8 / GBK / GB18030 编码，按「第X章」等规则自动分章
- EPUB 解析 OPF spine，按目录顺序加载正文
- 阅读页：章节目录、书签、字号、行距、4 种背景主题、上一章/下一章
- 阅读进度自动保存，下次打开原位续读

## 构建
推送到 `main` 分支或手动触发 workflow，产物：
- Actions artifact: `LocalReader-APK`
- Release: `v1.0` → `LocalReader-v1.0.apk`

签名固定使用仓库内 `reader-release.p12`（PKCS12，alias `reader`），可覆盖安装。

## 最低系统
Android 7.0（API 24），targetSdk 35。
