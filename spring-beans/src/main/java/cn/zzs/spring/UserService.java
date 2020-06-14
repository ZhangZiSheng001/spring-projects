package cn.zzs.spring;

public class UserService implements IUserService {
    
    private UserDao userDao;
    
    
    public UserService() {
        super();
        System.err.println("UserService构造方法被调用 --> ");
    }
    
    public void init() {
        System.err.println("UserService的init方法被调用 --> ");
    }
    
    
    public UserDao getUserDao() {
        return userDao;
    }
    
    public void setUserDao(UserDao userDao) {
        System.err.println("UserService的属性装配中 --> ");
        this.userDao = userDao;
    }
    
    public User get(String id) {
        return new User("zzs001", 18);
    }
    
    public void save(User user) {
        System.err.println("Service save user：" + user);
        userDao.save(user);
    }

    
}