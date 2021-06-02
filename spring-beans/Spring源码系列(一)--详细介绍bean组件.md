# 什么是spring-bean？

spring-bean 是 spring 家族中最核心的一个组件，从抽象层面来说，我们可以把它当成：

1. **通用的对象工厂**。这个有点像我们常用的`**Factory`，通过它，我们可以获取到所需的对象。
2. **全局的上下文**。我把某个对象丢进这个上下文，然后可以在应用的任何位置获取到这个对象。

# 本文要讲什么？

针对 spring-bean 组件，我计划分成 2 到 3 篇博客来分析。本文主要讲的是：

1. spring-bean 是什么？用来解决什么问题？
2. 几个重要的概念，例如什么是 bean？
3. 如何使用 spring-bean？

# spring-bean用来解决什么问题？

**spring-bean 主要是用来解耦实现类**。这里我用一个例子来说明，会更好理解一点。

假如我的业务系统使用的是 mysql 数据库，我通过下面的方式获取数据库驱动对象。

```java
public void save(User user) {
    java.sql.Driver driver = new com.mysql.cj.jdbc.Driver();
    Connection connection = driver.connect(url, properties);
    // do something
}
```

看着是没什么问题，然而，有一天我需要把数据库更换为 oracle，于是，我不得不更改代码。

```java
public void save(User user) {
    java.sql.Driver driver = new oracle.jdbc.driver.OracleDriver();
    Connection connection = driver.connect(url, properties);
    // do something
}
```

显然，这是不合理的。那么，有什么办法能做到不更改代码就能切换数据库呢？

JDBC 规范中使用了`DriverManager`来解耦数据库实现，可以做到不更改代码就能切换数据库。它是通过系统参数 + SPI 来找到建实现类的（SPI 的内容可以参考我的另一篇博客[使用SPI解耦你的实现类](https://www.cnblogs.com/ZhangZiSheng001/p/12114744.html)）。

```java
public void save(User user) {
    java.sql.Driver driver = DriverManager.getDriver(url);
    Connection connection = driver.connect(url, properties);
    // do something
}
```

大家应该都使用过 JDBC 吧，`DriverManager`的解耦效果，相信都见识到了。但是呢，我们的业务系统并不会采用这种方式来解耦自己的实现类。为什么呢？不难发现，我需要给`UserService`配套一个`UserServiceManager`，给`DepartmentService`配套一个`DepartmentServiceManager`······，这是非常繁琐的。类似的，常用的`**Factory`也存在同样的问题。

这时我们就会想，我不要那么多的`**Manager`或者`**Factory`行不行？有没有一个通用的对象工厂？

spring-bean 满足了这种需求，它就是一个通用的对象工厂，可以用来创建`UserService`，也可以用来创建`DepartmentService`。当然，前提是，你需要告诉 beanFactory 如何创建这个对象。

```java
public void save(User user) {
    java.sql.Driver driver = beanFactory.getBean(java.sql.Driver.class);
    Connection connection = driver.connect(url, properties);
    // do something
}
```

所以，spring-bean 本质上就是用来解耦实现类。除此之外，spring-bean 也是一个全局的上下文，我把某个对象丢进这个上下文，然后可以在应用的任何位置获取到这个对象。这个比较简单，就不展开讨论了。

# 几个重要的概念

在介绍如何使用 spring-bean 之前，先来看看几个重要的概念。

## 什么是bean

按照官方的说法， bean 是一个由 Spring IoC 容器实例化、组装和管理的对象。

**我认为，官方的表述是错误的**。在后面的使用例子中，我们会发现，如果纯粹把 spring-bean 当成一个全局的上下文，我们放进这个上下文的对象已经是一个完整的对象实例，并不会由 Spring IoC 实例化、组装，所以，更准确的表述应该是这样：

**通过 beanFactory 获取到的对象都属于 bean**。

至于什么是 IoC 容器，在 spring-bean 组件中，我认为，beanFactory 就属于 IoC 容器。

## 实例化、属性装配和初始化

在 spring-bean 组件的设计中，**实例化、属性装配和初始化，它们完整、有序地描述了创建一个新对象的整个流程**，它们是非常重要的理论基础。具体含义如下：

1. 实例化：new 一个新对象。
2. 属性装配：给对象的成员属性赋值。
3. 初始化：调用对象的初始化方法。

下面通过一段代码来简单演示下这个流程。

```java
public class User {
    
    private String name;
    
    private Integer age;
    
    public User() {
        super();
        System.err.println("主流程：User对象实例化中。。-->\n\t||\n\t\\/");
    }
    
    public void init() {
        System.err.println("主流程：User对象初始化中。。-->\n\t||\n\t\\/");
    }
    
    public void setName(String name) {
        System.err.println("主流程：User对象属性name装配中。。-->\n\t||\n\t\\/");
        this.name = name;
    }
}
```

如果我们将这个对象交给 spring-bean 管理，创建 bean 时会在控制台打印以下内容：

![spring-bean-test02](https://img2020.cnblogs.com/blog/1731892/202106/1731892-20210602224402788-283388598.png)

# 如何使用spring-bean

## 项目环境

JDK：1.8.0_231

maven：3.6.3 

IDE：Spring Tool Suites4 for Eclipse 4.12 

Spring：5.2.6.RELEASE

## 依赖引入

除了引入 spring，这里还额外引入了日志和单元测试（可选）。

```xml    
    <dependencies>
        <!-- spring -->
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-beans</artifactId>
            <version>5.2.6.RELEASE</version>
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
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>1.2.3</version>
            <type>jar</type>
        </dependency>
    </dependencies>
```

## 作为全局上下文使用

spring-bean 是一个全局的上下文，我把某个对象丢进这个上下文，然后可以在应用的任何位置获取到这个对象。注意，这种方式注册的 bean，实例化、属性装配和初始化并不由 spring-bean 来管理。

```java
    public void testContext() {
        // 创建beanFactory
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        
        User user = new User("zzs001", 18);
        // 存对象
        beanFactory.registerSingleton("user", user);
        
        // 取对象
        User user2 = (User)beanFactory.getBean("user");
        assertEquals(user, user2);
    }
```

## 作为对象工厂使用

如果把 spring-bean 当成对象工厂使用，我们需要告诉它如何创建对象，而 **beanDefinition 就包含了如何创建对象的所有信息**。

```java
    public void testObjectFactory() {
        // 创建beanFactory
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        // 定义一个beanDefinition
        BeanDefinition rootBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(User.class).getBeanDefinition();
        // 属性装配
        rootBeanDefinition.getPropertyValues().add("name", "zzs001");
        rootBeanDefinition.getPropertyValues().add("age", 18);
        // 初始化方法
        rootBeanDefinition.setInitMethodName("init");
        // 单例还是多例，默认单例
        rootBeanDefinition.setScope(BeanDefinition.SCOPE_PROTOTYPE);
        // 注册bean
        beanFactory.registerBeanDefinition("user", rootBeanDefinition);

        // 获取bean
        User user = (User)beanFactory.getBean("user");
        assertNotNull(user);
    }
```

## 多种获取bean的方式

实际使用中，我们更多的会使用 beanType 而不是 beanName 来获取 bean，beanFactory 也提供了相应的支持。我们甚至还可以同时使用 beanName 和 beanType，获取到指定 beanName 的 bean 后会进行类型检查，如果不通过，将会报错。

```java
    public void testGetBeanWays() {
        // 创建beanFactory
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        // 注册bean
        BeanDefinition rootBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(User.class).getBeanDefinition();
        beanFactory.registerBeanDefinition("user", rootBeanDefinition);

        // 获取bean--通过beanName
        User user1 = (User)beanFactory.getBean("user");
        assertNotNull(user1);
        
        // 获取bean--通过beanType
        User user2 = beanFactory.getBean(User.class);
        assertNotNull(user2);
        
        // 获取bean--通过beanName+beanType的方式
        User user3 = beanFactory.getBean("user", User.class);
        assertNotNull(user3);
    }
```

## 使用TypeConverter获取自定义类型的对象

在上面的例子中，当使用 beanName + beanType 来获取 bean 时，如果获取到的 bean 不是指定的类型，这时，并不会立即报错，beanFactory 会尝试使用合适`TypeConverter`来强制转换（需要我们注册上去）。

```java
    public void testTypeConverter() {

        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        // 注册类型转换器
        beanFactory.setTypeConverter(new TypeConverterSupport() {

            @SuppressWarnings("unchecked")
            @Override
            public <T> T convertIfNecessary(@Nullable Object value, @Nullable Class<T> requiredType, @Nullable TypeDescriptor typeDescriptor) throws TypeMismatchException {

                if(UserVO.class == requiredType && value instanceof User) {
                    User user = (User)value;
                    UserVO userVO = new UserVO();
                    userVO.setName(user.getName());
                    userVO.setAge(user.getAge());
                    return (T)userVO;
                }
                return null;
            }
        });

        BeanDefinition rootBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(User.class).getBeanDefinition();
        beanFactory.registerBeanDefinition("user", rootBeanDefinition);

        UserVO bean = beanFactory.getBean("user", UserVO.class);
        Assert.assertNotNull(bean);
    }
```

## bean冲突的处理

通过 beanName 获取 bean 和 通过 beanType 获取 bean 的区别在于，前者能唯一匹配到所需的 bean，后者就不一定了。如果我注册了两个相同 beanType 的 bean（这是允许的），通过 beanType 获取 bean 时就会报错。

```java
    public void testPrimary() {
        // 创建beanFactory
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        // 注册bean
        BeanDefinition rootBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(User.class).getBeanDefinition();
        beanFactory.registerBeanDefinition("user", rootBeanDefinition);
        beanFactory.registerSingleton("user2", new User("zzs002", 19));
        beanFactory.registerSingleton("user3", new User("zzs003", 18));

        // 获取bean
        User user = beanFactory.getBean(User.class);
        assertNotNull(user);
    }
```

运行以上方法，将出现 NoUniqueBeanDefinitionException 的异常。

![spring-bean-test01](https://img2020.cnblogs.com/blog/1731892/202106/1731892-20210602224441282-762155803.png)

通过 beanType 获取 bean 时，当存在多个同类型 bean 的时候，spring-bean 的处理逻辑是这样的：

1. 检查是否存在唯一一个`isPrimary = true`的 bean，存在的话将它返回；
2. 通过`OrderComparator`来计算每个 bean 的 priority，取 priority 最小的返回（`OrderComparator`需要我们自己注册）。注意，通过 registerSingleton 注册的和通过 registerBeanDefinition 注册的，比较的对象是不一样的，前者比较的对象是 bean 实例，后者比较的对象是 bean 类型，另外，这种方法不能存在相同 priority 的 bean。

所以，为了解决这种冲突，可以采取两种方法（1优先于2）：

1. **设置 beanDefinition 对象的 isPrimary = true**。不适用于 registerSingleton 的情况。
2. 为 beanFactory 注册`OrderComparator`（这种用的不多）。

代码如下：

```java
    public void testPrimary() {
        // 创建beanFactory
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        // 为BeanFactory设置比较器，比较少用
        beanFactory.setDependencyComparator(new OrderComparator() {
            @Override
            public Integer getPriority(Object obj) {
                return obj.hashCode();
            }
        });

        // 注册bean
        BeanDefinition rootBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(User.class).getBeanDefinition();
        // rootBeanDefinition.setPrimary(true); // 设置bean优先
        beanFactory.registerBeanDefinition("user", rootBeanDefinition);
        beanFactory.registerSingleton("user2", new User("zzs002", 19));
        beanFactory.registerSingleton("user3", new User("zzs003", 18));

        // 获取bean
        User user = beanFactory.getBean(User.class);
        assertNotNull(user);
    }
```

## 一种特殊的bean--FactoryBean

beanFactory 还支持注册一种特殊的对象--factoryBean，当我们获取 bean 时，拿到的不是这个 factoryBean，而是 factoryBean.getObject() 所返回的对象。那我就是想返回 factoryBean 怎么办？可以通过以下形式的 beanName 获取：一个或多个& + beanName。

```java
    public void testFactoryBean() throws BeansException, Exception {

        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        // 注册bean--注册一个 factoryBean
        UserFactoryBean userFactoryBean = new UserFactoryBean();
        beanFactory.registerSingleton("user", userFactoryBean);

        // 通过beanName获取
        assertEquals(User.class, beanFactory.getBean("user").getClass());
        
        // 通过beanType获取
        assertEquals(User.class, beanFactory.getBean(User.class).getClass());

        // 通过&+factoryBeanName的方式
        assertEquals(UserFactoryBean.class, beanFactory.getBean("&user").getClass());
    }
```

## 自动装配

默认情况下，beanFactory 会读取 beanDefinition 对象中的 propertyValues 来装配成员属性，所以，我们想要装配哪个成员属性，只要把键值对 add 进这个 propertyValues 就行。**前提是我们的 bean 必须包含对应成员属性的 setter 方法**。

spring-bean 还提供了更有趣的功能--自动装配。我只需要将 beanDefinition 的 autowireMode 设置为自动装配，beanFactory 就会帮我把包含 setter 方法的所有成员属性都赋值（当然，要有值才会赋）。

```java
    public void testAutowire() {
        // 创建beanFactory
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        // 注册userService
        AbstractBeanDefinition userServiceBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(UserService.class).getBeanDefinition();
        beanFactory.registerBeanDefinition("userService", userServiceBeanDefinition);
        
        // 注册userDao
        AbstractBeanDefinition userDaoBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(UserDao.class).getBeanDefinition();
        beanFactory.registerBeanDefinition("userDao", userDaoBeanDefinition);
        
        // 给userService设置装配属性userDao
        // userServiceBeanDefinition.getPropertyValues().add("userDao", userDaoBeanDefinition);
        userServiceBeanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);

        // 获取bean
        UserService userService = (UserService)beanFactory.getBean("userService");
        assertNotNull(userService.getUserDao());
    }
```

## bean 实例化、属性装配和初始化的处理器

前面讲到，我们将 bean 的实例化、属性装配和初始化都交给了 spring-bean 处理，然而，有时我们需要在这些节点对 bean 进行自定义的处理，这时就需要用到 beanPostProcessor。

这里我简单演示下如何添加处理器，以及处理器的执行时机，至于处理器的具体实现，我就不多扩展了。

```java
    public void testPostProcessor() {
        
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        // 添加实例化处理器
        beanFactory.addBeanPostProcessor(new InstantiationAwareBeanPostProcessor() {
            // 实例前处理
            // 如果这里我们返回了对象，则beanFactory会将它直接返回，不再进行bean的实例化、属性装配和初始化等操作
            public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
                System.out.println("处理器：bean实例化之前的处理。。 --> ");
                return null;
            }

            // 实例后处理
            // 这里判断是否继续对bean进行属性装配和初始化等操作
            public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
                System.out.println("处理器：bean实例化之后的处理。。 --> ");
                return true;
            }
        });

        // 添加装配处理器
        beanFactory.addBeanPostProcessor(new InstantiationAwareBeanPostProcessor() {
            // 属性装配前
            // 这里可以在属性装配前对参数列表进行调整
            public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) throws BeansException {
                System.out.println("处理器：属性装配前对参数列表进行调整。。--> ");
                return InstantiationAwareBeanPostProcessor.super.postProcessProperties(pvs, bean, beanName);
            }

        });

        // 添加初始化处理器
        beanFactory.addBeanPostProcessor(new BeanPostProcessor() {
            // 初始化前
            // 这里可以在初始化前对bean进行改造
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                System.out.println("处理器：初始化前，对bean进行改造。。 --> ");
                return bean;
            }

            // 初始化后
            // 这里可以在初始化后对bean进行改造
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                System.out.println("处理器：初始化后，对bean进行改造。。 --> ");
                return bean;
            }
        });
        
        // 定义一个beanDefinition
        BeanDefinition rootBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(User.class).getBeanDefinition();
        // 属性装配
        rootBeanDefinition.getPropertyValues().add("name", "zzs001");
        rootBeanDefinition.getPropertyValues().add("age", 18);
        // 初始化方法
        rootBeanDefinition.setInitMethodName("init");
        // 单例还是多例，默认单例
        rootBeanDefinition.setScope(BeanDefinition.SCOPE_PROTOTYPE);
        // 注册bean
        beanFactory.registerBeanDefinition("user", rootBeanDefinition);
        
        User user = (User)beanFactory.getBean("user");
        assertNotNull(user);
    }
```

运行以上方法，控制台打印出了整个处理流程。实际开发中，我们可以通过设置处理器来改造生成的 bean 。

![spring-bean-test03](https://img2020.cnblogs.com/blog/1731892/202106/1731892-20210602224510793-1748338641.png)

以上，基本介绍完 spring-bean 组件的使用。后续发现其他有趣的地方再做补充，也欢迎大家指正不足的地方。

最后，感谢阅读。

> 2021-06-02更改
>
> 相关源码请移步：[ spring-beans](https://github.com/ZhangZiSheng001/spring-projects/tree/master/spring-beans)

> 本文为原创文章，转载请附上原文出处链接：[https://www.cnblogs.com/ZhangZiSheng001/p/13126053.html](https://www.cnblogs.com/ZhangZiSheng001/p/13126053.html)