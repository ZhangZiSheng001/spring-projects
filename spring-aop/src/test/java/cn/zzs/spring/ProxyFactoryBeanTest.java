package cn.zzs.spring;

import java.util.concurrent.TimeUnit;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/**
 * 测试ProxyFactoryBeanTest
 * @author zzs
 * @date 2020年9月12日 上午11:07:35
 */
public class ProxyFactoryBeanTest {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyFactoryBeanTest.class);
    
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
}
