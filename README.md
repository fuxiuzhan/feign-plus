# feign-plus

## 使用的框架

```text
使用fuled-framework框架提供的基础框架能力，包括注册中心，配置中心，动态组件。
替换feign底层的http协议为netty长链接实现。使用私有协议栈，基本从上层到底层解释了
整个rpc的过程（包含服务注册发现及rpc过程），在此基础上去理解微服务的rpc原理，尤其是服务
的动态管理和协议的问题会更容易理解。
```

## 使用说明

- 1、首先需要将项目的maven仓库配置文件设置为resources/settings.xml

```text
IntelliJIDEA->Preferences->搜索maven override user setting 配置即可
这个配置是单个项目的配置，不影响其他项目。框架的依赖目前没有上传到公用maven仓库，所以使用的私有仓库

```

- 2、启动参数要增加-Denv=prd

```text
表示启动环境使用prd环境，这个参数控制配置中心和注册中心的namespace
nacos地址：http://prd.nacos.fuled.xyz:8848/nacos
```

- 3、需要设置cat监控的配置文件

```text
win电脑可在项目根目录创建data\appdatas\cat目录，将resources/client.xml 文件复制到cat目录中即可。
mac电脑则需要创建/data/appdatas/cat目录（创建方法见：https://www.jianshu.com/p/c2972f5586c4），将resources/client.xml 复制到cat目录

[!]
也可以不创建，不过要需求项目启动类的注解：
将@EnableFuledBoot注解修改为：

@SpringBootApplication(
    exclude = {NacosDiscoveryAutoConfiguration.class, NacosServiceRegistryAutoConfiguration.class}
)
@EnableSimpleCache
@EnableDiamondConfig

其实就是去掉@EnableCatTracing 注解，不让cat生效。
```
