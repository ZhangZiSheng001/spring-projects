package cn.zzs.spring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.TypeConverterSupport;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.lang.Nullable;

/**
 * 测试BeanFactory
 * @author zzs
 * @date 2020年5月11日 上午9:14:31
 */
public class BeanFactoryTest {
    /**
     * spring-bean 是一个全局的上下文，我把某个对象丢进这个上下文，然后可以在应用的任何位置获取到这个对象。
     * 这种方式注册的 bean，实例化、属性装配和初始化并不由 spring-bean 来管理。
     * @author zzs
     * @date 2021年6月2日 上午11:06:18 void
     */
    @Test
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

    /**
     * 如果把 spring-bean 当成对象工厂使用，我们需要告诉它如何创建对象，而 beanDefinition 就包含了如何创建对象的所有信息。
     * @author zzs
     * @date 2021年5月31日 下午4:48:03 void
     */
    @Test
    public void testObjectFactory() {
        // 创建beanFactory
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        // 定义一个beanDefinition
        BeanDefinition rootBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(User.class).getBeanDefinition();
        // 属性装配
        //rootBeanDefinition.getConstructorArgumentValues().addIndexedArgumentValue(0, "zzs001");
        //rootBeanDefinition.getConstructorArgumentValues().addIndexedArgumentValue(1, 18);
        rootBeanDefinition.getPropertyValues().add("name", "zzs001");
        rootBeanDefinition.getPropertyValues().add("age", "18");
        rootBeanDefinition.getPropertyValues().add("address.name", "波斯尼亚和黑塞哥维那");
        rootBeanDefinition.getPropertyValues().add("hobbies[0]", "发呆");
        rootBeanDefinition.getPropertyValues().add("hobbies[1]", "睡觉");
        // 初始化方法
        rootBeanDefinition.setInitMethodName("init");
        // 单例还是多例，默认单例
        // rootBeanDefinition.setScope(BeanDefinition.SCOPE_PROTOTYPE);
        // 注册bean
        beanFactory.registerBeanDefinition("user", rootBeanDefinition);

        // 获取bean
        User user = (User)beanFactory.getBean("user");
        assertNotNull(user);
    }

    /**
     * 实际使用中，我们更多的会使用 beanType 而不是 beanName 来获取 bean，beanFactory 也提供了支持。
     * 我们甚至还可以同时使用 beanName 和 beanType，获取到指定 beanName 的 bean 后会进行类型检查，如果不通过，将会报错。
     * @author zzs
     * @date 2021年5月31日 下午3:49:30 void
     */
    @Test
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
    
    /**
     * 当使用 beanName + beanType 来获取 bean 时，如果获取到的 bean 不是指定的类型，这时，并不会立即报错，beanFactory 会尝试使用合适`TypeConverter`来强制转换（需要我们注册上去）。
     * @author zzs
     * @date 2021年5月31日 下午4:39:05 void
     */
    @Test
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

    /**
     * 当出现多个同类型的bean时，如果使用类型获取，会报错NoUniqueBeanDefinitionException。可以通过以下方法解决（1优先于2）：
     * <p>1. 设置BeanDefinition对象为isPrimary。不适用于registerSingleton的情况
     * <p>2. 为BeanFactory设置比较器（这种用的不多）
     * @author zzs
     * @date 2020年6月14日 下午12:19:40
     * @return void
     */
    @Test
    public void testPrimary() {
        // 创建beanFactory
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        // 为BeanFactory设置比较器，比较少用
        /*beanFactory.setDependencyComparator(new OrderComparator() {
        
            @Override
            public Integer getPriority(Object obj) {
                return obj.hashCode();
            }
        });*/


        // 注册bean
        BeanDefinition rootBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(User.class).getBeanDefinition();
        // rootBeanDefinition.setAutowireCandidate(false);
        rootBeanDefinition.setPrimary(true); // 设置bean优先
        beanFactory.registerBeanDefinition("user", rootBeanDefinition);
        beanFactory.registerSingleton("user2", new User("zzs002", 19));
        //beanFactory.registerSingleton("user3", new User("zzs003", 18));

        // 获取bean
        User user = beanFactory.getBean(User.class);
        assertNotNull(user);
    }

    /**
     * beanFactory 还支持注册一种特殊的对象--factoryBean，当我们获取 bean 时，拿到的不是这个 factoryBean，而是 factoryBean.getObject() 所返回的对象。
     * 那我就是想返回 factoryBean 怎么办？可以通过以下形式的 beanName 获取：一个或多个& + beanName。
     * @author zzs
     * @date 2020年6月7日 下午2:59:53
     * @return void
     * @throws Exception 
     * @throws BeansException 
     */
    @Test
    public void testFactoryBean() throws BeansException, Exception {

        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        // 注册bean--注册一个 factoryBean
        UserFactoryBean userFactoryBean = new UserFactoryBean();
        beanFactory.registerSingleton("user", userFactoryBean);

        // 通过beanName获取
        assertEquals(User.class, beanFactory.getBean("user").getClass());
        
        // 通过beanType获取
        assertEquals(User.class, beanFactory.getBean(User.class).getClass());

        // 通过&+FactoryBeanName的方式
        assertEquals(UserFactoryBean.class, beanFactory.getBean("&user").getClass());
    }


    /**
     * 默认情况下，beanFactory 会读取 beanDefinition 对象中的 propertyValues 来装配成员属性，
     * 所以，我们想要装配哪个成员属性，只要把键值对 add 进这个 propertyValues 就行。**前提是我们的 bean 必须包含对应成员属性的 setter 方法**。
     * spring-bean 还提供了更有趣的功能--自动装配。
     * 我只需要将 beanDefinition 的 autowireMode 设置为自动装配，beanFactory 就会帮我把包含 setter 方法的所有成员属性都赋值（当然，要有值才会赋）。
     * @author zzs
     * @date 2021年5月31日 下午4:39:28
     */
    @Test
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
        // userServiceBeanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
        // userServiceBeanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_NAME);
        userServiceBeanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);

        // 获取bean
        UserService userService = (UserService)beanFactory.getBean("userService");
        assertNotNull(userService.getUserDao());
    }
    
    
    /**
     * 我们将 bean 的实例化、属性装配和初始化都交给了 spring-bean 处理，然而，有时我们需要在这些节点对 bean 进行自定义的处理，这时就需要用到 beanPostProcessor。
     * @author zzs
     * @date 2021年5月31日 下午4:42:15 void
     */
    @Test
    public void testPostProcessor() {
        
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        // 添加实例化处理器
        beanFactory.addBeanPostProcessor(new InstantiationAwareBeanPostProcessor() {
            // 实例前处理
            // 如果这里我们返回了对象，则beanFactory会将它直接返回，不再进行bean的实例化、属性装配和初始化等操作
            public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
                System.out.println("处理器：bean实例化之前的处理。。 -->\n\t||\n\t\\/");
                return null;
            }

            // 实例后处理
            // 这里判断是否继续对bean进行属性装配和初始化等操作
            public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
                System.out.println("处理器：bean实例化之后的处理。。 -->\n\t||\n\t\\/");
                return true;
            }
        });

        // 添加装配处理器
        beanFactory.addBeanPostProcessor(new InstantiationAwareBeanPostProcessor() {
            // 属性装配前
            // 这里可以在属性装配前对参数列表进行调整
            public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) throws BeansException {
                System.out.println("处理器：属性装配前对参数列表进行调整。。-->\n\t||\n\t\\/");
                return InstantiationAwareBeanPostProcessor.super.postProcessProperties(pvs, bean, beanName);
            }

        });

        // 添加初始化处理器
        beanFactory.addBeanPostProcessor(new BeanPostProcessor() {
            // 初始化前
            // 这里可以在初始化前对bean进行改造
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                System.out.println("处理器：初始化前，对bean进行改造。。-->\n\t||\n\t\\/");
                return bean;
            }

            // 初始化后
            // 这里可以在初始化后对bean进行改造
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                System.out.println("处理器：初始化后，对bean进行改造。。 -->\n\t||\n\t\\/");
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
    
    /**
     * bean 的属性装配是支持循环依赖的。只是我们需要注意 bean 的 scope 对循环依赖的影响，如下：
     * <p>已知 userService 和 userDao 相互依赖，且它们均为多例，这时将报错：无法解析的循环依赖。
     * <p>如果 userService 为单例，userDao 为多例，这时会有两种情况：
     * <p>如果你先获取的是 userDao，那么会报错
     * <p>如果你先获取的是 userService，则不会报错；
     * @author zzs
     * @date 2022年1月17日 下午11:25:07 void
     */
    @Test
    public void testCircularReference() {
        // 创建beanFactory
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        // 注册userService
        AbstractBeanDefinition userServiceBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(UserService.class).getBeanDefinition();
        //userServiceBeanDefinition.setScope(ConfigurableBeanFactory.SCOPE_PROTOTYPE);
        userServiceBeanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
        beanFactory.registerBeanDefinition("userService", userServiceBeanDefinition);
        
        // 注册userDao
        AbstractBeanDefinition userDaoBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(UserDao.class).getBeanDefinition();
        userDaoBeanDefinition.setScope(ConfigurableBeanFactory.SCOPE_PROTOTYPE);
        userDaoBeanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
        beanFactory.registerBeanDefinition("userDao", userDaoBeanDefinition);

        // 获取bean
        UserService userService = (UserService)beanFactory.getBean("userService");
        assertNotNull(userService.getUserDao());
        UserDao userDao = (UserDao)beanFactory.getBean("userDao");
        assertNotNull(userDao.getUserService());
    }

}
