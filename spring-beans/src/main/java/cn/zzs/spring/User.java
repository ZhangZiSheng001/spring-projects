package cn.zzs.spring;

public class User {
    
    private String name;
    
    private Integer age;
    
    public User() {
        super();
        System.err.println("主流程：User对象实例化中。。-->\n\t||\n\t\\/");
    }
    
    public User(String name, Integer age) {
        super();
        System.err.println("主流程：User对象实例化中。。-->\n\t||\n\t\\/");
        this.name = name;
        this.age = age;
    }
    
    
    public void init() {
        System.err.println("主流程：User对象初始化中。。-->\n\t||\n\t\\/");
    }
    
    
    public String getName() {
        return name;
    }
    
    
    
    public void setName(String name) {
        System.err.println("主流程：User对象属性name装配中。。-->\n\t||\n\t\\/");
        this.name = name;
    }
    
    
    
    public Integer getAge() {
        return age;
    }
    
    
    
    public void setAge(Integer age) {
        System.err.println("主流程：User对象属性age装配中。。-->\n\t||\n\t\\/");
        this.age = age;
    }
    
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("User [name=");
        builder.append(name);
        builder.append(", age=");
        builder.append(age);
        builder.append("]");
        return builder.toString();
    }



    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((age == null) ? 0 : age.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }



    @Override
    public boolean equals(Object obj) {
        if(this == obj)
            return true;
        if(obj == null)
            return false;
        if(getClass() != obj.getClass())
            return false;
        User other = (User)obj;
        if(age == null) {
            if(other.age != null)
                return false;
        } else if(!age.equals(other.age))
            return false;
        if(name == null) {
            if(other.name != null)
                return false;
        } else if(!name.equals(other.name))
            return false;
        return true;
    }


}
