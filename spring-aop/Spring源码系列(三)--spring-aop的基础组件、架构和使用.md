# 简介

前面已经讲完 spring-bean( 详见[Spring](https://www.cnblogs.com/ZhangZiSheng001/category/1776792.html) )，这篇博客开始攻克 Spring 的另一个重要模块--spring-aop。

spring-aop 可以实现动态代理（底层是使用 JDK 动态代理或 cglib 来生成代理类），在项目中，一般被用来实现日志、权限、事务等的统一管理。

我将通过两篇博客来详细介绍 spring-aop 的使用、源码等。这是第一篇博客，主要介绍 spring-aop 的组件、架构、使用等。

# 项目环境

maven：3.6.3

操作系统：win10

JDK：8u231

Spring：5.2.6.RELEASE


# 几个重要的组件

说到 spring-aop，我们经常会使用到**`Pointcut`、`Joinpoint`、`Advice`、`Aspect`**等等基础组件，它们都是抽象出来的“标准”，有的来自 aopalliance，有的来自 AspectJ，也有的是 spring-aop 原创。

**想要学好 spring-aop，必须理解好这几个基础组件**。但是，理解它们非常难，一个原因是网上能讲清楚的不多，第二个原因是这些组件本身抽象得不够直观（spring 官网承认了这一点）。

## AOP联盟的组件--Joinpoint、Advice

在  spring-aop 的包中内嵌了 aopalliance 的包（aopalliance 就是一个制定 AOP 标准的联盟、组织），这个包是 AOP 联盟提供的一套“标准”，提供了 AOP 一些通用的组件，包的结构大致如下。

```powershell
└─org
    └─aopalliance
        ├─aop
        │      Advice.class
        │      AspectException.class
        │
        └─intercept
                ConstructorInterceptor.class
                ConstructorInvocation.class
                Interceptor.class
                Invocation.class
                Joinpoint.class
                MethodInterceptor.class
                MethodInvocation.class
```

完整的 aopalliance 包，除了 aop 和 intercept，还包括了 instrument 和 reflect，后面这两个部分 spring-aop 没有引入，这里就不说了。

使用 UML 表示以上类的关系，如下。可以看到，这主要包含两个部分：`Joinpoint`和`Advice`。

<img src="https://img2020.cnblogs.com/blog/1731892/202009/1731892-20200915090605295-325173928.png" alt="AopAopallianceUML"  />

1. **Joinpoint**：**一个事件，包括调用某个方法（构造方法或成员方法）、操作某个成员属性等**。

例如，我调用了`user.study()` 方法，这个事件本身就属于一个`Joinpoint`。`Joinpoint`是一个“动态”的概念，通过它可以获取这个事件的静态信息，例如当前事件对应的`AccessibleObject`（`AccessibleObject`是`Field`、`Method`、`Constructor`等的超类）。spring-aop 主要使用到`Joinpoint`的子接口`MethodInvocation`。

2. **Advice**：**对`Joinpoint`执行的某些操作**。

例如，JDK 动态代理使用的`InvocationHandler`、cglib 使用的`MethodInterceptor`，在抽象概念上可以算是`Advice`（即使它们没有继承`Advice`）。spring-aop 主要使用到`Advice`的子接口`MethodInterceptor`。

3. **Joinpoint 和 Advice 的关系**：

`Joinpoint`是`Advice`操作的对象，一个`Advice`可以操作多个`Joinpoint`，一个`Joinpoint`也可以被多个`Advice`操作。**在 spring-aop 里，`Joinpoint`对象会持有一条`Advice`链，调用`Joinpoint.proceed()`将逐一执行其中的`Advice`（需要判断是否执行），执行完`Advice`链`Advice`链，将最终执行被代理对象的方法**。

## AspectJ 的组件--Pointcut、Aspect

AspectJ 是一个非常非常强大的 AOP 工具，可以实现编译期织入、编译后织入和类加载时织入，并且提供了一套 AspectJ 语法（spring-aop 支持这套语法，但要额外引入 aspectjweaver 包）。spring-aop 使用到了 AspectJ 的两个组件，`Pointcut`和`Aspect`。

其中，**`Pointcut`可以看成一个过滤器，它可以用来判断当前`Advice`是否拦截指定`Joinpoint`**，如下图所示。注意，不同的`Advice`也可以共用一个`Pointcut`。

<img src="https://img2020.cnblogs.com/blog/1731892/202009/1731892-20200915090626002-1373379548.png" alt="Pointcut_01" style="zoom:80%;" />

`Aspect`这个没什么特别，就是一组`Pointcut`+`Advice`的集合。下面这段代码中，有两个`Advice`，分别为`printRequest`和`printResponse`，它们共享同一个`Pointcut`，而这个类里的`Pointcut`+`Advice`可以算是一个`Aspect`。

```java
@Aspect
public class UserServiceAspect {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceAspect.class);
    
    @Pointcut("execution(* cn.zzs.spring.UserService+.*(..)))")
    public void genericPointCut() {

    }
    
    @Before(value = "genericPointCut()")
    public void printRequest(JoinPoint joinPoint) throws InterruptedException {
        //······
    }  
    
    @After(value = "genericPointCut()")
    public void printResponse(JoinPoint joinPoint) throws InterruptedException {
      //······;
    }  
}
```

## spring-aop 的组件--Advisor

`Advisor`是 spring-aop 原创的一个组件，**一个`Advice`加上它对应的过滤器，就组成了一个`Advisor`**。在上面例子中，`printRequest`的`Advice`加上它的`Pointcut`，就是一个`Advisor`。而`Aspect`由多个`Advisor`组成。

注意，这里的过滤器，可以是`Pointcut`，也可以是 spring-aop 自定义的`ClassFilter`。

# spring-aop 和 AspectJ 的关系

从 AOP 的功能完善程度来讲，AspectJ 支持编译期织入、编译后织入和类加载时织入，并且提供了一套 AspectJ 语法，非常强大。在 AspectJ 面前，spring-aop 就是个“小弟弟”。

spring-aop 之所以和 AspectJ 产生关联，主要是因为借鉴了 AspectJ 语法（这套语法一般使用注解实现，用于定义`Aspect`、`Pointcut`、`Advice`等），包括使用到 AspectJ 的注解以及解析语法的类。如果我们希望在 spring-aop 中使用 AspectJ 注解语法，需要额外引入 aspectjweaver 包。

# 如何使用 spring-aop

接下来展示的代码可能有的人看了会觉得奇怪，“怎么和我平时用 spring-aop 不一样呢？”。这里先说明一点，**因为本文讲的是 spring-aop，所以，我用的都是 spring-aop 的 API**，而实际项目中，由于 spring 封装了一层又一层，导致我们感知不到 spring-aop 的存在。

通常情况下，Spring 是通过向`BeanFactory`注册`BeanPostProcessor`（例如，`AbstractAdvisingBeanPostProcessor`）的方式对 bean 进行动态代理，原理并不复杂，相关内容可以通过 spring-bean 了解（ [Spring源码系列(二)--bean组件的源码分析](https://www.cnblogs.com/ZhangZiSheng001/p/13196228.html) ）。

接下来让我们抛开这些“高级封装”，看看 spring-aop 的真面目。

## spring-aop 的代理工厂

下面通过一个 UML 图来了解下 spring-aop 的结构，如下。

![ProxyFactoryUML](https://img2020.cnblogs.com/blog/1731892/202009/1731892-20200915090711597-1505551851.png)

spring-aop 为我们提供了三种代理工厂，其中`ProxyFactory`比较普通，`AspectJProxyFactory`支持 AspectJ 语法的代理工厂，`ProxyFactoryBean`可以给 Spring IoC 管理的 bean 进行代理。

下面介绍如何使用这些代理工厂来获得代理类。

## 使用ProxyFactory生成代理类

`ProxyFactory`的测试代码如下，如果指定了接口，一般会使用 JDK 动态代理，否则使用 cglib。

```java
    @Test
    public void test01() {
    
        ProxyFactory proxyFactory = new ProxyFactory();
        
        // 设置被代理的对象
        proxyFactory.setTarget(new UserService());
        // 设置代理接口--如果设置了接口，一般会使用JDK动态代理，否则用cglib
        // proxyFactory.setInterfaces(IUserService.class);
        
        // 添加第一个Advice
        proxyFactory.addAdvice(new MethodInterceptor() {
            
            public Object invoke(MethodInvocation invocation) throws Throwable {
                TimeUnit.SECONDS.sleep(1);
                
                LOGGER.info("打印{}方法的日志", invocation.getMethod().getName());
                // 执行下一个Advice
                return invocation.proceed();
            }
        });
        
        // 添加第二个Advice······
        
        IUserService userController = (IUserService)proxyFactory.getProxy();
        userController.save();
        userController.delete();
        userController.update();
        userController.find();
    }
```

运行以上方法，可以看到控制台输出：

```powershell
2020-09-12 16:32:02.704 [main] INFO  cn.zzs.spring.ProxyFactoryTest - 打印save方法的日志
增加用户
2020-09-12 16:32:03.725 [main] INFO  cn.zzs.spring.ProxyFactoryTest - 打印delete方法的日志
删除用户
2020-09-12 16:32:04.726 [main] INFO  cn.zzs.spring.ProxyFactoryTest - 打印update方法的日志
修改用户
2020-09-12 16:32:05.726 [main] INFO  cn.zzs.spring.ProxyFactoryTest - 打印find方法的日志
查找用户
```

## 使用ProxyFactoryBean生成代理类

`ProxyFactoryBean`和`ProxyFactory`差不多，区别在于`ProxyFactoryBean`的 target 是一个 bean。因为需要和 bean 打交道，所以这里需要创建 beanFactory 以及注册 bean。另外，我们可以设置每次生成的代理类都不同。

```java
    @Test
    public void test01() {
        // 注册bean
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("userService", 
                BeanDefinitionBuilder.rootBeanDefinition(UserService.class).getBeanDefinition());
        
        ProxyFactoryBean proxyFactory = new ProxyFactoryBean();
        // 设置beanFactory
        proxyFactory.setBeanFactory(beanFactory);
        // 设置被代理的bean
        proxyFactory.setTargetName("userService");
        // 添加Advice
        proxyFactory.addAdvice(new MethodInterceptor() {
            
            public Object invoke(MethodInvocation invocation) throws Throwable {
                TimeUnit.SECONDS.sleep(1);
                
                LOGGER.info("打印{}方法的日志", invocation.getMethod().getName());
                return invocation.proceed();
            }
        });
        // 设置scope
        //proxyFactory.setSingleton(true);
        proxyFactory.setSingleton(false);
        
        IUserService userController = (IUserService)proxyFactory.getObject();
        
        userController.save();
        userController.delete();
        userController.update();
        userController.find();
        
        IUserService userController2 = (IUserService)proxyFactory.getObject();
        System.err.println(userController == userController2);
    }
```

## 使用AspectJProxyFactory生成代理类

使用`AspectJProxyFactory`要额外引入 aspectjweaver 包，如下：

```xml
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>1.9.6</version>
            <!-- <scope>runtime</scope> -->
        </dependency>
```

接下来配置一个`Aspect`，如下。这里定义了一个`Advice`，即 printRequest 方法；定义了一个`Pointcut`，即拦截`UserService`及其子类。

```java
@Aspect
public class UserServiceAspect {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceAspect.class);
    
    @Pointcut("execution(* cn.zzs.spring.UserService+.*(..)))")
    public void genericPointCut() {

    }
    
    @Before(value = "genericPointCut()")
    public void printRequest(JoinPoint joinPoint) throws InterruptedException {
        TimeUnit.SECONDS.sleep(1);
        LOGGER.info("call {}_{} with args:{}", 
                joinPoint.getSignature().getDeclaringType().getSimpleName(), 
                joinPoint.getSignature().getName(), 
                joinPoint.getArgs());
    }  
}
```

编写生成代理类的方法，如下。`AspectJProxyFactory`会利用 AspectJ 的类来解析 Aspect，并转换为 spring-aop 需要的`Advisor`。

```java
    @Test
    public void test01() {
        
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory();
        // 设置被代理对象
        proxyFactory.setTarget(new UserService());
        
        // 添加Aspect
        proxyFactory.addAspect(UserServiceAspect.class);
        
        // 提前过滤不符合Poincut的类，这样在执行 Advice chain 的时候就不要再过滤
        proxyFactory.setPreFiltered(true);
        
        UserService userController = (UserService)proxyFactory.getProxy();
        
        userController.save();
        userController.delete();
        userController.update();
        userController.find();
    }
```

关于 spring-aop 的组件、架构、使用等内容，就介绍到这里，第二篇博客再分析具体源码。

感谢阅读。以上内容如有错误，欢迎指正。 

> 相关源码请移步：[ spring-aop]( https://github.com/ZhangZiSheng001/spring-projects/tree/master/spring-aop )

> 本文为原创文章，转载请附上原文出处链接：[https://www.cnblogs.com/ZhangZiSheng001/p/13671149.html](https://www.cnblogs.com/ZhangZiSheng001/p/13671149.html)