package cn.zzs.spring;

/**
 * 地址
 * @author zzs
 * @date 2021年6月8日 下午6:07:13
 */
public class Address {
    
    
    private String name;
    
    
    private String region;


    
    public String getName() {
        return name;
    }



    
    public void setName(String name) {
        this.name = name;
    }



    
    public String getRegion() {
        return region;
    }



    
    public void setRegion(String region) {
        this.region = region;
    }



    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Address [name=");
        builder.append(name);
        builder.append(", region=");
        builder.append(region);
        builder.append("]");
        return builder.toString();
    }




    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((region == null) ? 0 : region.hashCode());
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
        Address other = (Address)obj;
        if(name == null) {
            if(other.name != null)
                return false;
        } else if(!name.equals(other.name))
            return false;
        if(region == null) {
            if(other.region != null)
                return false;
        } else if(!region.equals(other.region))
            return false;
        return true;
    }
    
    
}
