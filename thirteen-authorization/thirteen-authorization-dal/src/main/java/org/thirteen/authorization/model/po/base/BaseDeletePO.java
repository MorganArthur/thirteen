package org.thirteen.authorization.model.po.base;

import lombok.*;

import javax.persistence.Column;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.MappedSuperclass;

/**
 * @author Aaron.Sun
 * @description 通用实体类（包含删除标记信息的实体类的基类）
 * @date Created in 15:23 2018/1/11
 * @modified by
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@MappedSuperclass
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class BaseDeletePO extends BasePO {

    private static final long serialVersionUID = 1L;
    /** 删除标记（0：正常；1：删除） */
    public static final String DEL_FLAG_NORMAL = "0";
    public static final String DEL_FLAG_DELETE = "1";
    /** 0：正常；1：删除 */
    @Column(name = "del_flag", columnDefinition = "CHAR(1) NOT NULL COMMENT '删除标记 0：正常；1：删除'")
    private String delFlag;

    /**
     * 判断当前对象是否为已被删除
     *
     * @return 当前对象是否为已被删除
     */
    public boolean isDeleted() {
        return !DEL_FLAG_NORMAL.equals(this.delFlag);
    }

    /**
     * 设置当前对象删除标记为未删除
     *
     * @param <T> 当前类的子类
     * @return 当前对象
     */
    @SuppressWarnings("unchecked")
    public <T extends BaseDeletePO> T normal() {
        this.setDelFlag(DEL_FLAG_NORMAL);
        return (T) this;
    }

    /**
     * 设置当前对象删除标记为已删除
     *
     * @param <T> 当前类的子类
     * @return 当前对象
     */
    @SuppressWarnings("unchecked")
    public <T extends BaseDeletePO> T delete() {
        this.setDelFlag(DEL_FLAG_DELETE);
        return (T) this;
    }
}