# 简介

spring-bean 组件是 Spring IoC 的核心，我们可以使用它的 beanFactory 来获取所需的对象，对象的实例化、属性装配和初始化等都可以交给 spring 来管理。 

针对 spring-bean 组件，我计划分成两篇博客来讲解。本文会详细介绍这个组件，包括以下内容。下一篇再具体分析它的源码。

1. spring-bean 组件的相关概念：实例化、属性装配、初始化、bean、beanDefinition、beanFactory。
2. bean 组件的使用：注册bean、获取bean、属性装配、处理器等。

# 项目环境说明

正文开始前，先介绍下示例代码使用的环境等。

## 工程环境

JDK：1.8.0_231

maven：3.6.1 

IDE：Spring Tool Suites4 for Eclipse 4.12 

Spring：5.2.6.RELEASE

## 依赖引入

除了引入 spring，这里还额外引入了日志和单元测试。

```xml
    <properties>
        <spring.version>5.2.6.RELEASE</spring.version>
    </properties>
    
    <dependencies>
        <!-- spring -->
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-core</artifactId>
            <version>${spring.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-beans</artifactId>
            <version>${spring.version}</version>
        </dependency>
		<!-- junit -->
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <version>4.12</version>
            <scope>test</scope>
        </dependency>
        <!-- logback -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>1.7.28</version>
            <type>jar</type>
            <scope>compile</scope>
        </dependency>
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-core</artifactId>
            <version>1.2.3</version>
            <type>jar</type>
        </dependency>
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>1.2.3</version>
            <type>jar</type>
        </dependency>
    </dependencies>
```

# 几个重要概念

## 实例化、属性装配和初始化

在 spring-bean 组件的设计中，这三个词完整、有序地描述了生成一个新对象的整个流程，是非常重要的理论基础。它们的具体含义如下：

1. 实例化：创建出一个新对象。
2. 属性装配：给对象的成员属性赋值。
3. 初始化：调用对象的初始化方法。

下面使用一段代码来简单演示下这个流程。

```java
public class UserService implements IUserService {
    
    private UserDao userDao;
    
    
    public UserService() {
        super();
        System.err.println("UserService构造方法被调用");
        System.err.println("        ||");
        System.err.println("        \\/");
    }
    
    public void init() {
        System.err.println("UserService的init方法被调用");
        System.err.println("        ||");
        System.err.println("        \\/");
    }
    
    
    public UserDao getUserDao() {
        return userDao;
    }
    
    public void setUserDao(UserDao userDao) {
        System.err.println("UserService的属性装配中");
        System.err.println("        ||");
        System.err.println("        \\/");
        this.userDao = userDao;
    }
    
}
```

如果我们将这个 bean 交给 spring 管理，获取 bean 时会在控制台打印以下内容：

<img src="https://img2020.cnblogs.com/blog/1731892/202006/1731892-20200614181515068-560016589.png" alt="spring-bean-test02" style="zoom:80%;" />


## 什么是bean

按照官方的说法， bean 是一个由 Spring IoC 容器实例化、组装和管理的对象。我认为，这种表述是错误的，通过`registerSingleton`方式注册的 bean，它就不是由 Spring IoC 容器实例化、组装，所以，更准确的表述应该是这样：

**某个类的对象、FactoryBean 对象、描述对象或 FactoryBean 描述对象，被注册到了 Spring IoC 容器，这时通过 Spring IoC 容器获取的这个类的对象就是 bean。**

举个例子，使用了 Spring 的项目中， Controller 对象、Service 对象、DAO 对象等都属于 bean。

至于什么是 IoC 容器，在 spring-bean 组件中，我认为，beanFactory 就属于 IoC 容器。

## 什么是beanFactory

从客户端来看，一个完整的 beanFactory 工厂包含以下基本功能:

1. 注册别名。对应下图的`AliasRegistry`接口。
2. 注册单例对象。对应下图的`SingletonBeanRegistry`接口。
3. 注册`BeanDefinition`对象。对应下图的`BeanDefinitionRegistry`接口。
4. 获取 bean。对应下图的`BeanFactory`接口。

在 spring-bean 组件中，`DefaultListableBeanFactory`就是一个完整的 beanFactory 工厂，也可以说是一个 IoC 容器。接下来的例子将直接使用它来作为 beanFactory。

![BeanFactoryUML_01](https://img2020.cnblogs.com/blog/1731892/202006/1731892-20200614181541484-2054058872.png)



至于其他的接口，这里也补充说明下。`HierarchicalBeanFactory`用于提供父子工厂的支持，`ConfigurableBeanFactory`用于提供配置 beanFactory 的支持，`ListableBeanFactory`用于提供批量获取 bean 的支持（不包含父工厂的 bean），`AutowireCapableBeanFactory`用于提供实例化、属性装配、初始化等一系列管理 bean 生命周期的支持。


## 什么是beanDefinition

beanDefinaition 是一个描述对象，用来描述 bean 的实例化、初始化等信息。

在 spring-bean 组件中，beanDefinaition主要包含以下四种:

1. `RootBeanDefinition`：beanFactory 中最终用于 createBean 的 beanDefinaition，**不允许添加 parentName**。在 BeanFactory 中以下三种实现类都会被包装成`RootBeanDefinition`用于 createBean。
2. `ChildBeanDefinition`：**必须设置 parentName** 的 beanDefinaition。当某个 Bean 的描述对象和另外一个的差不多时，我们可以直接定义一个`ChildBeanDefinition`，并设置它的 parentName 为另外一个的 beanName，这样就不用重新设置一份。
3. `GenericBeanDefinition`：通用的 beanDefinaition，**可以设置 parentName，也可以不用设置**。
4. `AnnotatedGenericBeanDefinition`：在`GenericBeanDefinition`基础上增加暴露注解数据的方法。

<img src="https://img2020.cnblogs.com/blog/1731892/202006/1731892-20200614181609832-984078630.png" alt="BeanDefinationUML_01" style="zoom:80%;" />


spring-bean 组件提供了`BeanDefinitionBuilder`用于创建 beanDefinaition，下面的例子会频繁使用到。

# 使用例子

## 入门--简单地注册和获取bean

下面通过一个入门例子来介绍注册和获取 bean 的过程。

```java
    @Test
    public void testBase() {
        // 创建BeanFactory对象
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        
        // 创建BeanDefinition对象
        BeanDefinition rootBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(UserService.class).getBeanDefinition();
        
        // 注册Bean
        beanFactory.registerBeanDefinition("userService", rootBeanDefinition);
        
        // 获取Bean
        IUserService userService = (IUserService)beanFactory.getBean("userService");
        System.err.println(userService.get("userId"));
    }
```

## 两种注册bean的方式

beanFactory 除了支持注册 beanDefinition，还允许直接注册 bean 实例，如下。和前者相比，后者的实例化、属性装配和初始化都没有交给 spring 管理。

```java
    @Test
    public void testRegisterWays() {
        // 创建BeanFactory对象
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        
        // 注册Bean-- BeanDefinition方式
        BeanDefinition rootBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(UserService.class).getBeanDefinition();
        beanFactory.registerBeanDefinition("userService", rootBeanDefinition);
        
        // 注册Bean-- Bean实例方式
        beanFactory.registerSingleton("userService2", new UserService());
        
        // 获取Bean
        IUserService userService = (IUserService)beanFactory.getBean("userService");
        System.err.println(userService.get("userId"));
        IUserService userService2 = (IUserService)beanFactory.getBean("userService2");
        System.err.println(userService2.get("userId"));
    }
```

当然，这种方式仅支持单例 bean 的注册，多例的就没办法了。

## 注册多例bean

默认情况下，我们从 beanFactory 获取到的 bean 都是单例的，即每次 getBean 获取到的都是同一个对象，实际项目中，有时我们需要获取到多例的 bean，这个时候就可以通过设置 beanDefinition 的 scope 来处理。如下：

```java
    @Test
    public void testScope() {
        // 创建BeanFactory对象
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        
        // 注册Bean-- BeanDefinition方式
        BeanDefinition rootBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(UserService.class).getBeanDefinition();
        rootBeanDefinition.setScope(BeanDefinition.SCOPE_PROTOTYPE);
        beanFactory.registerBeanDefinition("userService", rootBeanDefinition);
        
        // 获取Bean--通过BeanType
        IUserService userService1 = beanFactory.getBean(IUserService.class);
        IUserService userService2 = beanFactory.getBean(IUserService.class);
        assertNotEquals(userService1, userService2);
    }
```

## 多种获取bean的方式

beanFactory 提供了多种方式来获取 bean 实例，如下。如果同时使用 beanName 和 beanType，获取到指定 beanName 的 bean 后会进行类型检查和类型类型，如果都不通过，将会报错。

```java
    @Test
    public void testGetBeanWays() {
        // 创建BeanFactory对象
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        
        // 创建BeanDefinition对象
        BeanDefinition rootBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(UserService.class).getBeanDefinition();
        
        // 注册Bean
        beanFactory.registerBeanDefinition("userService", rootBeanDefinition);
        
        // 获取Bean--通过BeanName
        IUserService userService = (IUserService)beanFactory.getBean("userService");
        System.err.println(userService.get("userId"));
        // 获取Bean--通过BeanType
        IUserService userService2 = beanFactory.getBean(IUserService.class);
        System.err.println(userService2.get("userId"));
        // 获取Bean--通过BeanName+BeanType的方式
        IUserService userService3 = beanFactory.getBean("userService", IUserService.class);
        System.err.println(userService3.get("userId"));
    }
```

## 不同形式的beanName

通过 name 获取 bean，这个 name 包含以下三种形式：

1. beanName，即注册 bean 时用的 beanName。这是使用最多的形式，需要注意一点，如果 beanName 对应的 bean 是`FactoryBean`，并不会返回`FactoryBean`的实例，而是会返回`FactoryBean.getObject`方法的返回结果。
2. alias，即我们通过`SimpleAliasRegistry.registerAlias(name, alias)`方法注册到 beanFactory 的别名。这时，需要将 name 解析为 alias 对应的 beanName 来获取 bean。
3. '&' + factorybeanName，这时为了获取`FactoryBean`的一种特殊格式。

```java
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        // 注册Bean--注册的是一个 FactoryBean
        UserServiceFactoryBean userServiceFactoryBean = new UserServiceFactoryBean();
        beanFactory.registerSingleton("userServiceFactoryBean", userServiceFactoryBean);

        // 注册BeanName的别名
        beanFactory.registerAlias("userServiceFactoryBean", "userServiceAlias01");

        // 通过BeanName获取
        assertEquals(userServiceFactoryBean.getObject(), beanFactory.getBean("userServiceFactoryBean"));

        // 通过别名获取
        assertEquals(userServiceFactoryBean.getObject(), beanFactory.getBean("userServiceAlias01"));

        // 通过&+FactoryBeanName的方式
        assertEquals(userServiceFactoryBean, beanFactory.getBean("&userServiceFactoryBean"));
```


## bean冲突的处理

通过 beanType 的方式获取 bean，如果存在多个同类型的 bean且无法确定最优先的那一个，就会报错。

```java
    @Test
    public void testPrimary() {
        // 创建BeanFactory对象
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory(); 
        
        // 创建BeanDefinition对象
        BeanDefinition rootBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(User.class).getBeanDefinition();
        
        // 注册Bean
        beanFactory.registerBeanDefinition("UserRegisterBeanDefinition", rootBeanDefinition);
        beanFactory.registerSingleton("UserRegisterSingleton", new User("zzs002", 19));
        beanFactory.registerSingleton("UserRegisterSingleton2", new User("zzs002", 18));
        
        // 获取Bean--通过BeanType
        User user = beanFactory.getBean(User.class);
        System.err.println(user);
    }
```

运行以上方法，将出现 NoUniqueBeanDefinitionException 的异常。

![spring-bean-test01](https://img2020.cnblogs.com/blog/1731892/202006/1731892-20200614181638832-358781947.png)

针对上面的这种问题，spring 的处理方法如下：

1. 检查是否存在唯一一个通过`registerBeanDefinition`且`isPrimary = true`的（存在多个会报错），存在的话将它作为匹配到的唯一 beanName；
2. 通过我们注册的`OrderComparator`来确定优先值最小的作为唯一  beanName。注意，通过`registerSingleton`注册的和通过`registerBeanDefinition`注册的，比较的对象是不一样的，前者比较的对象是 bean 实例，后者比较的对象是 bean 类型，另外，这种方法最好不要存在相同优先级的 bean。

所以，为了解决这种冲突，可以设置`BeanDefinition`对象的 isPrimary = true，或者为 beanFactory 设置`OrderComparator`，代码如下：

```java
    @Test
    public void testPrimary() {
        // 创建BeanFactory对象
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        // 为BeanFactory设置比较器
        beanFactory.setDependencyComparator(new OrderComparator() {

            @Override
            public Integer getPriority(Object obj) {
                return obj.hashCode();
            }
        });

        // 创建BeanDefinition对象
        BeanDefinition rootBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(User.class).getBeanDefinition();
        // rootBeanDefinition.setPrimary(true); // 设置BeanDefinition对象为isPrimary

        // 注册Bean
        beanFactory.registerBeanDefinition("userRegisterBeanDefinition", rootBeanDefinition);
        beanFactory.registerSingleton("userRegisterSingleton", new User("zzs002", 19));
        beanFactory.registerSingleton("userRegisterSingleton2", new User("zzs003", 18));

        // 获取Bean--通过BeanType
        User user = beanFactory.getBean(User.class);
        System.err.println(user);
    }
```

## 使用TypeConverter获取自定义类型的对象

当我们使用 beanType 来获取 bean 时，如果获取到的 bean 不是指定的类型，这时，不会立即报错，beanFactory 会尝试使用我们注册的`TypeConverter`来强制转换。而这个类型转换器我们可以自定义设置，如下。

```java
    @Test
    public void testTypeConverter() {
        
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        // 注册类型转换器
        beanFactory.setTypeConverter(new TypeConverterSupport() {
            @SuppressWarnings("unchecked")
            @Override
            public <T> T convertIfNecessary(@Nullable Object value, @Nullable Class<T> requiredType,
                    @Nullable TypeDescriptor typeDescriptor) throws TypeMismatchException {
                // 将User转换为UserVO
                if(UserVO.class.equals(requiredType) && User.class.isInstance(value)) {
                    User user = (User)value;
                    return (T)new UserVO(user);
                }
                return null;
            }
        });

        BeanDefinition rootBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(User.class).getBeanDefinition();
        beanFactory.registerBeanDefinition("User", rootBeanDefinition);

        UserVO bean = beanFactory.getBean("User", UserVO.class);
        Assert.assertTrue(UserVO.class.isInstance(bean));
    }
```

## 属性装配

beanFactory 在进行属性装配时，会读取 beanDefinition 对象中的`PropertyValues`中的propertyName=propertyValue，所以，我们想要对 bean 注入什么参数，只要在定义 beanDefinition 时指定就行。

```java
    @Test
    public void testPopulate() {
        // 创建BeanFactory对象
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        // 定义userService的beanDefinition
        AbstractBeanDefinition userServiceBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(UserService.class).getBeanDefinition();
        // 定义userDao的beanDefinition
        AbstractBeanDefinition userDaoBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(UserDao.class).getBeanDefinition();
        // 给userService设置装配属性
        userServiceBeanDefinition.getPropertyValues().add("userDao", userDaoBeanDefinition);
        // userServiceBeanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
        // userServiceBeanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_NAME);

        // 注册Bean
        beanFactory.registerBeanDefinition("userService", userServiceBeanDefinition);
        beanFactory.registerBeanDefinition("userDao", userDaoBeanDefinition);

        // 获取Bean
        IUserService userService = (IUserService)beanFactory.getBean("userService");
        userService.save(null);
    }
```

运行以上方法，发现 userDao 对象被成功注入到了 userService 对象中！

这里补充一点，beanFactory 除了通过 beanDefinition 中的`PropertyValues`获取 propertyName=propertyValue，还可以读取 bean 中的属性来自动定义 propertyName=propertyValue，只要设置 beanDefinition 的 autowireMode 就可以了。

## bean 实例化、属性装配和初始化的处理器

前面讲到，我们将 bean 的实例化、属性装配和初始化都交给了 spring 处理，然而，有时我们需要在这些节点对 bean 进行自定义的处理，这时就需要用到 beanPostProcessor。

这里我简单演示下如何添加处理器，以及处理器的执行时机，至于处理器的具体实现，我就不多扩展了。

```java
    @Test
    public void testPostProcessor() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        // 添加实例化处理器
        beanFactory.addBeanPostProcessor(new InstantiationAwareBeanPostProcessor() {
            // 如果这里我们返回了对象，则beanFactory会将它作为bean直接返回，不再进行bean的实例化、属性装配和初始化等操作
            public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
                if(UserService.class.equals(beanClass)) {
                    System.err.println("bean实例化之前的处理。。 --> ");
                }
                return null;
            }

            // 这里通过返回的布尔值判断是否需要继续对bean进行属性装配和初始化等操作
            public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
                if(UserService.class.isInstance(bean)) {
                    System.err.println("bean实例化之后的处理。。 --> ");
                }
                return true;
            }
        });

        // 添加装配处理器
        beanFactory.addBeanPostProcessor(new InstantiationAwareBeanPostProcessor() {

            // 这里可以在属性装配前对参数列表进行调整
            public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) throws BeansException {
                if(UserService.class.isInstance(bean)) {
                    System.err.println("属性装配前对参数列表进行调整 --> ");
                }
                return InstantiationAwareBeanPostProcessor.super.postProcessProperties(pvs, bean, beanName);
            }

        });

        // 添加初始化处理器
        beanFactory.addBeanPostProcessor(new BeanPostProcessor() {

            // 初始化前对bean进行改造
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                if(UserService.class.isInstance(bean)) {
                    System.err.println("初始化前，对Bean进行改造。。 --> ");
                }
                return bean;
            }

            // 初始化后对bean进行改造
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if(UserService.class.isInstance(bean)) {
                    System.err.println("初始化后，对Bean进行改造。。 --> ");
                }
                return bean;
            }
        });
        // 定义userService的beanDefinition
        AbstractBeanDefinition userServiceBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(UserService.class).getBeanDefinition();
        // 定义userDao的beanDefinition
        AbstractBeanDefinition userDaoBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(UserDao.class).getBeanDefinition();
        // 给userService添加装配属性
        userServiceBeanDefinition.getPropertyValues().add("userDao", userDaoBeanDefinition);
        // 给userService设置初始化方法
        userServiceBeanDefinition.setInitMethodName("init");
        
        // 注册bean
        beanFactory.registerBeanDefinition("userService", userServiceBeanDefinition);
        beanFactory.registerBeanDefinition("userDao", userDaoBeanDefinition);

        IUserService userService = (IUserService)beanFactory.getBean("userService");
        System.err.println(userService.get("userId"));
    }
```

运行以上方法，控制台打印出了整个处理流程。实际开发中，我们可以通过设置处理器来改变改造生成的 bean 。

<img src="https://img2020.cnblogs.com/blog/1731892/202006/1731892-20200626201418174-124423364.png" alt="spring-bean-test03" style="zoom:80%;" />

以上，基本介绍完 spring-bean 组件的使用，下篇博客再分析源码，如果在分析过程中发现有其他特性，也会在这篇博客的基础上扩展。

> 相关源码请移步：[ spring-beans](https://github.com/ZhangZiSheng001/spring-projects/tree/master/spring-beans)

> 本文为原创文章，转载请附上原文出处链接：[https://www.cnblogs.com/ZhangZiSheng001/p/13126053.html](https://www.cnblogs.com/ZhangZiSheng001/p/13126053.html)













