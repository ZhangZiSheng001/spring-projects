package cn.zzs.spring;

public interface IUserService {
    User get(String id);
    
    void save(User user);
}
