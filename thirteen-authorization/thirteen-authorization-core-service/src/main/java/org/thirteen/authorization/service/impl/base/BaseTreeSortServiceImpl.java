package org.thirteen.authorization.service.impl.base;

import org.thirteen.authorization.dozer.DozerMapper;
import org.thirteen.authorization.model.params.base.BaseParam;
import org.thirteen.authorization.model.params.base.CriteriaParam;
import org.thirteen.authorization.model.po.base.BaseTreeSortPO;
import org.thirteen.authorization.model.vo.base.BaseTreeSortVO;
import org.thirteen.authorization.repository.base.BaseRepository;
import org.thirteen.authorization.service.base.BaseTreeSortService;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.thirteen.authorization.service.support.base.ModelInformation.CODE_FIELD;
import static org.thirteen.authorization.service.support.base.ModelInformation.PARENT_CODE_FIELD;

/**
 * @author Aaron.Sun
 * @description 通用Service层接口实现类（实体类为上下级结构）
 * @date Created in 18:15 2020/1/15
 * @modified by
 */
public abstract class BaseTreeSortServiceImpl<VO extends BaseTreeSortVO, PO extends BaseTreeSortPO, R extends BaseRepository<PO, String>>
    extends BaseRecordServiceImpl<VO, PO, R> implements BaseTreeSortService<VO> {

    public BaseTreeSortServiceImpl(R baseRepository, DozerMapper dozerMapper, EntityManager em) {
        super(baseRepository, dozerMapper, em);
    }

    @Override
    public VO findParent(String code) {
        VO model = this.findOneByParam(BaseParam.of().add(CriteriaParam.equal(CODE_FIELD, code).and()));
        return Objects.nonNull(model) ? this.findOneByParam(BaseParam.of()
            .add(CriteriaParam.equal(PARENT_CODE_FIELD, model.getParentCode()).and())) : null;
    }

    @Override
    public List<VO> findAllParent(String code) {
        List<VO> parents = new ArrayList<>();
        VO model = this.findOneByParam(BaseParam.of().add(CriteriaParam.equal(CODE_FIELD, code).and()));
        while (model != null) {
            parents.add(model);
            model = this.findOneByParam(BaseParam.of().add(CriteriaParam.equal(CODE_FIELD, model.getParentCode()).and()));
        }
        return parents;
    }

    @Override
    public List<VO> findAllChildren(String code) {
        List<VO> children = this.findAllByParam(BaseParam.of()
            .add(CriteriaParam.equal(PARENT_CODE_FIELD, code).and())).getList();
        if (children != null && children.size() > 0) {
            for (VO obj : children) {
                children.addAll(this.findAllChildren(obj.getCode()));
            }
        }
        return children;
    }

}