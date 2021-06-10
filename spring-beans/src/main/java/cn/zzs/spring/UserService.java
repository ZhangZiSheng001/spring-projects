package cn.zzs.spring;

public class UserService implements IUserService {
    
    private UserDao userDao;
    
    public UserService(UserDao userDao) {
        super();
        this.userDao = userDao;
        System.err.println("UserService1构造方法被调用 --> ");
    }
    
    public UserService() {
        super();
        System.err.println("UserService1构造方法被调用 --> ");
    }
    
    public void init() {
        System.err.println("UserService1的init方法被调用 --> ");
    }
    
    
    public UserDao getUserDao() {
        return userDao;
    }
    
    public void setUserDao(UserDao userDao) {
        System.err.println("UserService1装配属性userDao --> ");
        this.userDao = userDao;
    }
    
    public User get(String id) {
        return new User("zzs001", 18);
    }
    
    public void save(User user) {
        System.err.println("UserService1 save user：" + user);
        userDao.save(user);
    }

    
}