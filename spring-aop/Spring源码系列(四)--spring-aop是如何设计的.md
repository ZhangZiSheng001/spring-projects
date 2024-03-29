# 简介

顾名思义，spring-aop 是用来做 AOP 开发的，搭配 spring-bean 一起使用的话，AOP 将更加解耦、方便。在实际项目中，spring-aop 被广泛用来实现日志、权限、事务、异常等的统一管理。

上一篇博客（[Spring源码系列(三)--spring-aop的基础组件、架构和使用]( https://www.cnblogs.com/ZhangZiSheng001/p/13671149.html )）简单讲了 spring-aop 的基础组件、架构和使用方法，本文将开始研究 spring-aop 的源码，主要分成以下部分：

1. spring-aop 的几个重要的组件，如 Joinpoint、Advice、Pointcut、Advisor 等；
2. spring-aop 是如何设计的

# 项目环境

maven：3.6.3

操作系统：win10

JDK：8u231

Spring：5.2.6.RELEASE

# 一点补充

在第一篇博客中，我们使用 spring-aop 提供的代理工厂来生成动态代理类，被代理的对象可以是我们自己 new 的一个对象，也可以是 bean。**因为 spring-aop 最主要的功能就是生成动态代理类，所以，本文的源码分析都只围绕这个功能展开，并不会掺杂 spring-bean 的内容**。

实际项目中，Spring 可以“悄无声息”地完成对 bean 的代理，本质是通过注册`BeanPostProcessor`来实现，原理并不复杂。如果你对 spring-bean 感兴趣的话，可以参考博客[Spring源码系列(二)--bean组件的源码分析](https://www.cnblogs.com/ZhangZiSheng001/p/13196228.html)。

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

#  spring-aop是如何设计的

了解了 spring-aop 的重要组件，接下来就可以构建它的设计视图。**spring-aop 的设计视图主要包括两个部分：生成代理类和代理方法的执行**。

## 生成代理类

这里我画了一张 UML 图来简单说明。

![ProxyFactoryUML](https://img2020.cnblogs.com/blog/1731892/202109/1731892-20210927143035862-2039404617.png)

**spring-aop 采用动态代理为目标类生成一个代理对象，Joinpoint 的组装和 advice chain 的执行都是在这个代理对象中完成，而不是通过层层代理的方式来实现**。

`AdvisedSupport`里持有了一条 advice chain，严格来说应该是 advisor List。

`AopProxy`用来生成代理对象，spring-aop 提供了 JDK 动态代理和 cglib 动态代理两种`AopProxy`实现。

除此之外，spring-aop 提供了三种代理工厂供调用者使用，其中`ProxyFactory`比较普通，`AspectJProxyFactory`支持 AspectJ 语法的代理工厂，`ProxyFactoryBean`可以给 Spring IoC 管理的 bean 进行代理。上一篇博客已介绍过如何使用这三个代理工厂。

## 代理方法的执行

这里使用 cglib 的代理类来简单说明代理方法的执行过程。关于 cglib 的内容可以参考： [源码详解系列(一)--cglib动态代理的使用和分析](https://www.cnblogs.com/ZhangZiSheng001/p/11917086.html) 

![ProxyInvokeUML](https://img2020.cnblogs.com/blog/1731892/202009/1731892-20200928155332644-2015180344.png)

当我们调用代理的方法时，代理方法中将生成一个`Joinpoint`对象--即图中的`CglibMethodInvocation`，它持有了一条`Advice chain`，而`Advice chain`通过 Advisor List 过滤得到，调用`Joinpoint`的`proceed()`方法就可以执行`Advice chain`。

以上简单介绍了 spring-aop 的设计视图，有了这些，相信读者会更容易读懂具体的源码。

感谢阅读。以上内容如有错误，欢迎指正。 

> 2021-09-27更改

> 相关源码请移步：[spring-aop]( https://github.com/ZhangZiSheng001/spring-projects/tree/master/spring-aop )

> 本文为原创文章，转载请附上原文出处链接：https://www.cnblogs.com/ZhangZiSheng001/p/13745168.html