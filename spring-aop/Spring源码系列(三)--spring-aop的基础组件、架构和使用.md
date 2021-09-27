# 简介

前面已经讲完 spring-bean( 详见[Spring](https://www.cnblogs.com/ZhangZiSheng001/category/1776792.html) )，这篇博客开始攻克 Spring 的另一个核心模块--spring-aop。

顾名思义，spring-aop 是用来做 AOP 开发的，搭配 spring-bean 一起使用的话，AOP 将更加解耦、方便。在实际项目中，spring-aop 被广泛用来实现日志、权限、事务、异常等的统一管理。

我将通过两篇博客来详细介绍 spring-aop 的使用、源码等。这是第一篇博客，主要介绍 spring-aop 的组件、架构、使用等。

# 项目环境

maven：3.6.3

操作系统：win10

JDK：8u231

Spring：5.2.6.RELEASE

# 几个重要的组件

说到 spring-aop，我们经常会提到**`Joinpoint`、`Advice`、`Pointcut`、`Aspect`、`Advisor`**等等概念，它们都是抽象出来的“标准”，有的来自 aopalliance，有的来自 AspectJ，也有的是 spring-aop 原创。

它们是构成 spring-aop “设计图”的基础，理解它们非常难，一个原因是网上能讲清楚的不多，第二个原因是这些组件本身抽象得不够直观（spring 官网承认了这一点）。

## 对Joinpoint做Advice

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

使用 UML 表示以上类的关系，如下。可以看到，这主要包含两个部分：**`Joinpoint`和`Advice`（这是 AOP 最核心的两个概念）**。完整的 aopalliance 包，除了 aop 和 intercept，还包括了 instrument 和 reflect，后面这两个部分 spring-aop 没有引入，这里就不说了。

![AopAopallianceUML](https://img2020.cnblogs.com/blog/1731892/202109/1731892-20210927141031977-1366903158.png)

1. **Joinpoint**

**`Joinpoint`表示对某个方法（构造方法或成员方法）或属性的调用**。

例如，我调用了 user.save() 方法，这个调用动作就属于一个`Joinpoint`。`Joinpoint`是一个“动态”的概念，`Field`、`Method`、`Constructor`等对象是它的静态部分。

如上图所示，**`Joinpoint`是`Advice`操作的对象**。

在 spring-aop 中，主要使用`Joinpoint`的子接口--`MethodInvocation`。

2. **Advice**

**对`Joinpoint`执行的某些操作**。

例如，JDK 动态代理使用的`InvocationHandler`、cglib 使用的`MethodInterceptor`，在抽象概念上可以算是`Advice`（即使它们没有继承`Advice`）。

在 spring-aop 中，主要使用`Advice`的子接口--`MethodInterceptor`。

为了更好地理解这两个概念，我再举一个例子：当我们对用户进行新增操作前，需要进行权限校验。其中，调用 user.save()  的动作就是一个的`Joinpoint`，权限校验就是一个`Advice`，即对`Joinpoint`（新增用户的动作）做`Advice`（权限校验）。

**在 spring-aop 中，`Joinpoint`对象持有了一条 Advice chain ，调用`Joinpoint`的`proceed()`方法将采用责任链的形式依次执行各个 Advice**（注意，`Advice`的执行可以互相嵌套，不是单纯的先后顺序）。cglib 和 JDK 动态代理的缺点就在于，它们没有所谓的 Advice chain，一个`Joinpoint`一般只能分配一个`Advice`，当需要使用多个`Advice`时，需要像套娃一样层层代理。

## 其他的几个概念

在 spring-aop 中，还会使用到其他的概念，例如`Advice Filter`、`Advisor`、`Pointcut`、`Aspect`等。这些概念的重要性不如`Joinpoint`、`Advice`，如果对深入了解 spring-aop 不感兴趣的话，可以不用了解。

1. **Advice Filter**

**Advice Filter 一般和`Advice`绑定，它用来告诉我们，`Advice`是否作用于指定的`Joinpoint`**，如果 true，则将`Advice`加入到当前`Joinpoint`的`Advice chain`，如果为 false，则不加入。

在 spring-aop 中，常用的 Advice Filter 包括`ClassFilter`和`MethodMatcher`，前者过滤的是类，后者过滤的是方法。

2. **`Pointcut`**

**`Pointcut`是 AspectJ 的组件，它是一种  Advice Filter**。

在 spring-aop 中，`Pointcut`=`ClassFilter`+`MethodMatcher`+。

3. **`Advisor`**

`Advisor`是 spring-aop 原创的组件，**一个 Advisor = 一个 Advice Filter + 一个 Advice**。

在 spring-aop 中，主要有两种`Advisor`：`IntroductionAdvisor`和`PointcutAdvisor`。前者为`ClassFilter`+`Advice`，后者为`Pointcut`+`Advice`。

4. **`Aspect`**

`Aspect`也是 AspectJ 的组件，一组同类的`PointcutAdvisor`的集合就是一个`Aspect`。

在下面代码中，printRequest 和 printResponse 都是`Advice`，genericPointCut 是`Pointcut`，printRequest + genericPointCut 是`PointcutAdvisor`，UserServiceAspect 是`Aspect`。

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

# spring-aop 和 AspectJ 的关系

从 AOP 的功能完善程度来讲，AspectJ 支持编译期织入、编译后织入和类加载时织入，并且提供了一套 AspectJ 语法，非常强大。在 AspectJ 面前，spring-aop 就是个“小弟弟”。

spring-aop 之所以和 AspectJ 产生关联，主要是因为借鉴了 AspectJ 语法（这套语法一般使用注解实现，用于定义`Aspect`、`Pointcut`、`Advice`等），包括使用到 AspectJ 的注解以及解析语法的类。如果我们希望在 spring-aop 中使用 AspectJ 注解语法，需要额外引入 aspectjweaver 包。

# 如何使用 spring-aop

接下来展示的代码可能有的人看了会觉得奇怪，怎么和我平时用 spring-aop 不一样呢？。这里先说明一点，**因为本文讲的是 spring-aop，所以，我用的都是 spring-aop 原生的 API**，而实际项目中，由于 spring 封装了一层又一层，导致我们感知不到 spring-aop 的存在。

通常情况下，Spring 是通过向`BeanFactory`注册`BeanPostProcessor`（例如，`AbstractAdvisingBeanPostProcessor`）的方式对 bean 使用 spring-aop 的功能，原理并不复杂，相关内容可以通过 spring-bean 了解（ [Spring源码系列(二)--bean组件的源码分析](https://www.cnblogs.com/ZhangZiSheng001/p/13196228.html) ）。

接下来让我们抛开这些“高级封装”，看看 spring-aop 的真面目。

## spring-aop 的代理工厂

下面通过一个 UML 图来了解下 spring-aop 的结构，如下。

![ProxyFactoryUML](https://img2020.cnblogs.com/blog/1731892/202109/1731892-20210927143035862-2039404617.png)

**spring-aop 采用动态代理为目标类生成一个代理对象，Joinpoint 的组装和 advice chain 的执行都是在这个代理对象中完成，而不是通过层层代理的方式来实现**。

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

> 2021-09-27更改

> 相关源码请移步：[ spring-aop]( https://github.com/ZhangZiSheng001/spring-projects/tree/master/spring-aop )

> 本文为原创文章，转载请附上原文出处链接：[https://www.cnblogs.com/ZhangZiSheng001/p/13671149.html](https://www.cnblogs.com/ZhangZiSheng001/p/13671149.html)