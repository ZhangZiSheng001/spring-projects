package cn.zzs.spring;

import org.springframework.beans.factory.FactoryBean;

public class UserFactoryBean implements FactoryBean<User> {
    
    @Override
    public User getObject() throws Exception {
        return new User("zzs001", 18);
    }

    @Override
    public Class<User> getObjectType() {
        return User.class;
    }
}
