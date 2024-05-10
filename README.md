# 工具箱 - mrcode

只支持 JDK 17+（可以复制源码到 JDK8 中使用，新特性只有一小部分，有些类可能都没有新特性）

## 简述
定位：平时开发中第三方工具无法满足的时候，自己写得，或则在基础上封装的一些工具

## 使用说明
添加依赖：

```xml
<!-- maven 使用 -->
<dependency>
    <groupId>cn.mrcode.tool</groupId>
    <artifactId>my-toolbox</artifactId>
    <version>0.1.4</version>
</dependency>
```
```groovy
// gradle 使用
implementation 'cn.mrcode.tool:my-toolbox:0.1.4'
```
可以通过 [Maven 中央仓库](https://central.sonatype.com/artifact/cn.mrcode.tool/my-toolbox) 获取最新版本

目前已有的工具有：
- [BatchProcessor](src%2Fmain%2Fjava%2Fcn%2Fmrcode%2Ftool%2Fmytoolbox%2Fthread%2FBatchProcessor.java)：多线程分批处理工具
- [TreeUtil](src%2Fmain%2Fjava%2Fcn%2Fmrcode%2Ftool%2Fmytoolbox%2Flang%2Ftree%2FTreeUtil.java)：树节点构建工具
- [KeyedLock](src%2Fmain%2Fjava%2Fcn%2Fmrcode%2Ftool%2Fmytoolbox%2Fconcurrent%2Fkeyedlock%2FKeyedLock.java)：多 key 锁工具
- [SimpleTaskDispatcher](src%2Fmain%2Fjava%2Fcn%2Fmrcode%2Ftool%2Fmytoolbox%2Fthread%2FSimpleTaskDispatcher.java)：简单任务分发器

具体使用方式可以查看 [在线文档](https://www.yuque.com/mrcode.cn/note-combat/ypxy8nhgzclg2psk) 或者查看 [测试用例](https://github.com/zq99299/my-toolbox/tree/main/src/test/java/cn/mrcode/tool/mytoolbox)

## 简要更新日志
### v0.1.4
- [SimpleTaskDispatcher](src%2Fmain%2Fjava%2Fcn%2Fmrcode%2Ftool%2Fmytoolbox%2Fthread%2FSimpleTaskDispatcher.java) 增加简单任务分发器

### v0.1.3
- BatchProcessor 增加 stopQuietly 方法，可以多次调用，不会抛出异常
### v0.1.2
- [BatchProcessor.java](src%2Fmain%2Fjava%2Fcn%2Fmrcode%2Ftool%2Fmytoolbox%2Fthread%2FBatchProcessor.java) 工具类增加 startListen 方法，该方法替代 start 方法作为主要入口方法，会更清晰知道自己要的是单条消费数据还是批量消费数据
