package cn.zzs.spring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.beans.PropertyDescriptor;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.TypeConverterSupport;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.OrderComparator;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.lang.Nullable;

/**
 * 测试BeanFactory
 * @author zzs
 * @date 2020年5月11日 上午9:14:31
 */
public class BeanFactoryTest {

    /**
     * 
     * 基本的一个流程：创建BeanFactory对象->创建BeanDefinition对象->注册Bean获取->Bean
     * @author zzs
     * @date 2020年6月14日 下午12:16:00
     * @return void
     */
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

    @Test
    public void testRegisterWays() {
        // 创建BeanFactory对象
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

        // 注册Bean-- BeanDefinition方式
        BeanDefinition rootBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(UserService.class).getBeanDefinition();
        beanFactory.registerBeanDefinition("userService", rootBeanDefinition);
        rootBeanDefinition.getPropertyValues().add("name", "zzs001");
        rootBeanDefinition.getPropertyValues().add("age", 18);

        // 注册Bean-- Bean实例方式
        beanFactory.registerSingleton("userService2", new UserService());

        // 获取Bean
        IUserService userService = (IUserService)beanFactory.getBean("userService");
        System.err.println(userService.get("userId"));
        IUserService userService2 = (IUserService)beanFactory.getBean("userService2");
        System.err.println(userService2.get("userId"));
    }

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

    /**
     * 
     * bean冲突的处理办法：
     * 1. 设置BeanDefinition对象为isPrimary。不适用于registerSingleton的情况
     * 2. 为BeanFactory设置比较器。
     * 其中1优先于2
     * @author zzs
     * @date 2020年6月14日 下午12:19:40
     * @return void
     */
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

    /**
     * 
     * getBean(String name),这个方法中的name支持以下三种形式：
     * 1.beanName。如果对应的Bean是FactoryBean，不会返回FactoryBean的实例，而是会返回FactoryBean对应的Bean的实例
     * 2.alias。一个beanName可以对应多个alias
     * 3.factoryBeanName。可以返回FactoryBean的实例，形式为：一个或多个& + beanName
     * @author zzs
     * @date 2020年6月7日 下午2:59:53
     * @return void
     * @throws Exception 
     * @throws BeansException 
     */
    @Test
    public void testGetBeanByBeanName() throws BeansException, Exception {

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
        
        System.err.println(beanFactory.getBean(UserServiceFactoryBean.class));;
    }

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

    @Test
    public void testTypeConverter() {

        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        // 注册类型转换器
        beanFactory.setTypeConverter(new TypeConverterSupport() {

            @SuppressWarnings("unchecked")
            @Override
            public <T> T convertIfNecessary(@Nullable Object value, @Nullable Class<T> requiredType, @Nullable TypeDescriptor typeDescriptor) throws TypeMismatchException {

                if(UserVO.class.equals(requiredType) && User.class.isInstance(value)) {
                    User user = (User)value;
                    return (T)new UserVO(user);
                }
                return null;
            }
        });

        BeanDefinition rootBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(User.class).getBeanDefinition();
        beanFactory.registerBeanDefinition("user", rootBeanDefinition);

        UserVO bean = beanFactory.getBean("user", UserVO.class);
        Assert.assertTrue(UserVO.class.isInstance(bean));
    }

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

}
