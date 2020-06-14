package cn.zzs.spring;

import org.springframework.beans.factory.FactoryBean;

public class UserServiceFactoryBean implements FactoryBean<UserService> {
    
    private UserService userService = new UserService();

    @Override
    public UserService getObject() throws Exception {
        return userService;
    }

    @Override
    public Class<UserService> getObjectType() {
        return UserService.class;
    }
}
