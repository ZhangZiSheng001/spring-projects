package cn.zzs.spring;

import org.junit.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

/**
 * 测试AspectJProxyFactory
 * @author zzs
 * @date 2020年9月10日 下午1:37:37
 */
public class AspectJProxyFactoryTest {
    
    
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
}
