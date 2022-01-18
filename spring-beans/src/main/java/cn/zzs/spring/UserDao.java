package cn.zzs.spring;



public class UserDao implements IUserDao {
    private UserService userService;
    
    public UserDao() {
        super();
        System.err.println("UserDao构造方法被调用 --> ");
    }
    
    public void save(User user) {
        System.err.println("Dao save user：" + user);
    }

    
    public UserService getUserService() {
        return userService;
    }

    
    public void setUserService(UserService userService) {
        System.err.println("UserDao装配属性userService --> ");
        this.userService = userService;
    }
    
}
