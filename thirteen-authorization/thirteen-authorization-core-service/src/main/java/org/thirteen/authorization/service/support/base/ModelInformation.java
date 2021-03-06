package org.thirteen.authorization.service.support.base;

import org.springframework.util.Assert;
import org.thirteen.authorization.common.utils.StringUtil;
import org.thirteen.authorization.exceptions.EntityErrorException;

import javax.persistence.Table;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Aaron.Sun
 * @description 通过反射获取model（PO、VO等对象）的信息，如：是否包含某属性，是否包含某方法，反射调用某方法等
 * @date Created in 10:26 2019/12/27
 * @modified by
 */
public class ModelInformation<T> {

    /**
     * 当前泛型真实类型的Class
     */
    private Class<T> realClass;
    /**
     * 模型对应的类名
     */
    private String className;
    /**
     * 模型对应的表名，仅PO模型存在表名
     */
    private String tableName;
    /**
     * 当前泛型对象中的所有属性，包含父类中的属性
     */
    private Field[] fields;
    /**
     * 主键ID字段
     */
    public static final String ID_FIELD = "id";
    /**
     * 编码字段
     */
    public static final String CODE_FIELD = "code";
    /**
     * 状态字段
     */
    public static final String ACTIVE_FIELD = "active";
    /**
     * 显示顺序字段
     */
    public static final String SORT_FIELD = "sort";
    /**
     * 上级编码字段
     */
    public static final String PARENT_CODE_FIELD = "parentCode";
    /**
     * 创建者字段
     */
    public static final String CREATE_BY_FIELD = "createBy";
    /**
     * 创建时间字段
     */
    public static final String CREATE_TIME_FIELD = "createTime";
    /**
     * 更新者字段
     */
    public static final String UPDATE_BY_FIELD = "updateBy";
    /**
     * 更新时间字段
     */
    public static final String UPDATE_TIME_FIELD = "updateTime";
    /**
     * 逻辑删除字段
     */
    public static final String DEL_FLAG_FIELD = "delFlag";
    /**
     * 版本号字段
     */
    public static final String VERSION_FIELD = "version";

    /**
     * 通过反射获取子类确定的泛型类
     */
    public ModelInformation(Class<T> domainType) {
        this.realClass = domainType;
        this.className = domainType.getSimpleName();
        Table table = this.realClass.getAnnotation(Table.class);
        if (table != null) {
            this.tableName = table.name();
        }
        // 通过while循环获取所有父类中的属性
        Class<?> clazz = domainType;
        List<Field> fieldList = new ArrayList<>();
        while (clazz != null) {
            fieldList.addAll(new ArrayList<>(Arrays.asList(clazz.getDeclaredFields())));
            clazz = clazz.getSuperclass();
        }
        this.fields = fieldList.toArray(new Field[0]);
    }

    public T newInstance() {
        try {
            return this.getRealClass().getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * 判断是否包含指定字段，不包含则抛出异常
     *
     * @param field 指定字段
     * @return 是否包含指定字段
     */
    public boolean contains(String field) {
        Assert.notNull(field, "The given field must not be null!");
        boolean flag = false;
        for (Field f : this.fields) {
            if (field.equals(f.getName())) {
                flag = true;
            }
        }
        return flag;
    }

    /**
     * 获取指定字段的set方法
     *
     * @param field          指定字段
     * @param parameterTypes set方法的入参类型
     * @return 目标类中指定字段的set方法
     * @throws EntityErrorException 目标类中无指定字段异常
     */
    public Method getSetter(String field, Class<?>... parameterTypes) throws EntityErrorException {
        Assert.notNull(field, "The given field must not be null!");
        Method method;
        // 字段的set方法名
        String setter = "set" + StringUtil.capitalize(field);
        try {
            method = realClass.getMethod(setter, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new EntityErrorException(e.getMessage(), e.getCause());
        }
        return method;
    }

    /**
     * 获取指定字段的get方法
     *
     * @param field 指定字段
     * @return 目标类中指定字段的get方法
     * @throws EntityErrorException 目标类中无指定字段异常
     */
    public Method getGetter(String field) throws EntityErrorException {
        Assert.notNull(field, "The given field must not be null!");
        Method method;
        // 字段的get方法名
        String getter = "get" + StringUtil.capitalize(field);
        try {
            method = realClass.getMethod(getter);
        } catch (NoSuchMethodException e) {
            throw new EntityErrorException(e.getMessage(), e.getCause());
        }
        return method;
    }

    /**
     * 调用指定的set方法
     *
     * @param field          指定字段
     * @param parameterTypes set方法的入参类型
     * @param obj            目标类对象
     * @param args           set方法的入参
     */
    public void invokeSet(String field, Class<?>[] parameterTypes, Object obj, Object... args) {
        Assert.notNull(field, "The given field must not be null!");
        Assert.notNull(obj, "Object must not be null!");
        invokeSet(getSetter(field, parameterTypes), obj, args);
    }

    /**
     * 调用指定的get方法
     *
     * @param field 指定字段
     * @param obj   目标类对象
     * @param args  get方法的入参
     */
    public Object invokeGet(String field, Object obj, Object... args) {
        Assert.notNull(field, "The given field must not be null!");
        Assert.notNull(obj, "Object must not be null!");
        return invokeGet(getGetter(field), obj, args);
    }

    /**
     * 调用指定的set方法
     *
     * @param setMethod set方法
     * @param obj       目标类对象
     * @param args      set方法的入参
     */
    public void invokeSet(Method setMethod, Object obj, Object... args) {
        Assert.notNull(obj, "Object must not be null!");
        try {
            setMethod.invoke(obj, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new EntityErrorException(e.getMessage(), e.getCause());
        }
    }

    /**
     * 调用指定的get方法
     *
     * @param getMethod get方法
     * @param obj       目标类对象
     * @param args      get方法的入参
     */
    public Object invokeGet(Method getMethod, Object obj, Object... args) {
        Assert.notNull(obj, "Object must not be null!");
        try {
            return getMethod.invoke(obj, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new EntityErrorException(e.getMessage(), e.getCause());
        }
    }

    /**
     * 获取对象的真实类型
     *
     * @return 对象的真实类型
     */
    public Class<T> getRealClass() {
        return this.realClass;
    }

    /**
     * 获取对象的类名
     *
     * @return 对象的类名
     */
    public String getClassName() {
        return this.className;
    }

    /**
     * 获取PO对象@Table对应的表名
     *
     * @return 对应的表名
     */
    public String getTableName() {
        return this.tableName;
    }

    /**
     * 获取对象的所有字段
     *
     * @return 对象的所有字段
     */
    public Field[] getFields() {
        return this.fields;
    }

    /**
     * 判断字段是否不会映射到数据库
     *
     * @param field 字段
     * @return 字段是否不会映射到数据库
     */
    public boolean isTransient(Field field) {
        return field.getAnnotation(javax.persistence.Transient.class) != null;
    }

}
