package cn.zzs.spring;

import org.springframework.beans.factory.annotation.Autowired;

public class UserService2 implements IUserService {

    @Autowired
    private UserDao userDao;
    
    
    public UserService2() {
        super();
        System.err.println("UserService2构造方法被调用 --> ");
    }
    
    public void init() {
        System.err.println("UserService2的init方法被调用 --> ");
    }
    
    
    public UserDao getUserDao() {
        return userDao;
    }
    
    public void setUserDao(UserDao userDao) {
        System.err.println("UserService2装配属性userDao --> ");
        this.userDao = userDao;
    }
    
    public User get(String id) {
        return new User("zzs002", 19);
    }
    
    public void save(User user) {
        System.err.println("UserService2 save user：" + user);
        userDao.save(user);
    }
}
