package cn.zzs.spring;



public class UserDao implements IUserDao {
    public void save(User user) {
        System.err.println("Dao save user：" + user);
    }
}
