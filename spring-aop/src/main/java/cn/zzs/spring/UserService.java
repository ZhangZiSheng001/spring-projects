package cn.zzs.spring;

public class UserService implements IUserService {

    public void save() {
        System.out.println("增加用户");
    }
    
    public void delete() {
        System.out.println("删除用户");
    }

    
    public void update() {
        System.out.println("修改用户");
    }

    public void find() {
        System.out.println("查找用户");
    }
    
}
