# 工具箱 - mrcode

## 简述
定位：平时开发中第三方工具无法满足的时候，自己写得，或则在基础上封装的一些工具

## 使用说明
添加依赖：

```xml
<!-- maven 使用 -->
<dependency>
    <groupId>cn.mrcode.tool</groupId>
    <artifactId>my-toolbox</artifactId>
    <version>0.1.1</version>
</dependency>
```
```groovy
// gradle 使用
implementation 'cn.mrcode.tool:my-toolbox:0.1.1'
```
可以通过 [Maven 中央仓库](https://central.sonatype.com/artifact/cn.mrcode.tool/my-toolbox) 获取最新版本

目前已有的工具有：
- BatchProcessor：多线程分批处理工具
- TreeUtil：树节点构建工具
- KeyedLock：多 key 锁工具

具体使用方式可以查看 [在线文档](https://www.yuque.com/mrcode.cn/note-combat/ypxy8nhgzclg2psk) 或者查看 [测试用例](https://github.com/zq99299/my-toolbox/tree/main/src/test/java/cn/mrcode/tool/mytoolbox)
