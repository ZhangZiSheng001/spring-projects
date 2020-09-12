package cn.zzs.spring;

import java.util.concurrent.TimeUnit;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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
