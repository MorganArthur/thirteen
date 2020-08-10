package org.thirteen.datamation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.thirteen.datamation.model.po.DmColumnPO;

/**
 * @author Aaron.Sun
 * @description 数据化列信息数据操作层接口
 * @date Created in 15:56 2020/7/27
 * @modified by
 */
@Repository
public interface DmColumnRepository extends JpaRepository<DmColumnPO, String>, JpaSpecificationExecutor<DmColumnPO> {
}