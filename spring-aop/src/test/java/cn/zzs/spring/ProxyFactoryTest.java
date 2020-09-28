package cn.zzs.spring;

import java.util.concurrent.TimeUnit;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;

/**
 * 测试ProxyFactory
 * @author zzs
 * @date 2020年9月9日 上午10:07:47
 */
public class ProxyFactoryTest {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyFactoryTest.class);
    
    @Test
    public void testCglibProxy() {
        
        // 设置输出代理类到指定路径
        // System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, "img");
        
        ProxyFactory proxyFactory = new ProxyFactory();
        
        // 设置被代理的对象
        proxyFactory.setTarget(new UserService());
        
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
    
    
    @Test
    public void testJdkProxy() {
        
        // 设置输出代理类到指定路径   使用启动参数才有效
        //System.getProperties().put("sun.misc.ProxyGenerator.saveGeneratedFiles", "true"); 
        
        ProxyFactory proxyFactory = new ProxyFactory();
        
        // 设置被代理的对象
        proxyFactory.setTarget(new UserService());
        
        // 设置代理接口
        proxyFactory.setInterfaces(IUserService.class);
        
        // 添加Advice
        proxyFactory.addAdvice(new MethodInterceptor() {
            
            public Object invoke(MethodInvocation invocation) throws Throwable {
                TimeUnit.SECONDS.sleep(1);
                
                LOGGER.info("打印{}方法的日志", invocation.getMethod().getName());
                return invocation.proceed();
            }
        });
        IUserService userController = (IUserService)proxyFactory.getProxy();
        
        userController.save();
        userController.delete();
        userController.update();
        userController.find();
    }
}
