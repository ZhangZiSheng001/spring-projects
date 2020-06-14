package cn.zzs.spring;

import org.springframework.beans.factory.annotation.Autowired;

public class UserService2 implements IUserService {

    @Autowired
    private UserDao userDao;
    
    
    public void save(User user) {
        System.err.println("Service2 save user：" + user);
        userDao.save(user);
    }


    @Override
    public User get(String id) {
        // TODO Auto-generated method stub
        return null;
    }
}
