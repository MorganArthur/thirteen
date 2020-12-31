package org.thirteen.datamation.core.spring;

import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.thirteen.datamation.core.criteria.DmCriteria;
import org.thirteen.datamation.core.criteria.DmQuery;
import org.thirteen.datamation.core.criteria.DmSpecification;
import org.thirteen.datamation.core.exception.DatamationException;
import org.thirteen.datamation.core.generate.ClassInfo;
import org.thirteen.datamation.core.generate.DmClassLoader;
import org.thirteen.datamation.core.generate.po.PoConverter;
import org.thirteen.datamation.core.generate.po.PoGenerator;
import org.thirteen.datamation.core.generate.repository.RepositoryConverter;
import org.thirteen.datamation.core.generate.repository.RepositoryGenerator;
import org.thirteen.datamation.core.orm.jpa.persistenceunit.PersistenceUnitInfoImpl;
import org.thirteen.datamation.model.po.DmColumnPO;
import org.thirteen.datamation.model.po.DmRelationPO;
import org.thirteen.datamation.model.po.DmTablePO;
import org.thirteen.datamation.repository.DmColumnRepository;
import org.thirteen.datamation.repository.DmRelationRepository;
import org.thirteen.datamation.repository.DmTableRepository;
import org.thirteen.datamation.util.CollectionUtils;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.uncapitalize;
import static org.thirteen.datamation.core.DmCodes.DEL_FLAG_NORMAL;

/**
 * @author Aaron.Sun
 * @description 数据化repository
 * @date Created in 17:30 2020/8/18
 * @modified by
 */

public class DatamationRepository implements ApplicationContextAware {

    /** spring中，repository的bean定义中，属性entityManager的key */
    private static final String SPRING_ENTITY_MANAGER = "entityManager";
    /** spring中，repository的bean定义中，属性transactionManager的key */
    private static final String SPRING_TRANSACTION_MANAGER = "transactionManager";

    /** 手动生成的bean统一添加前缀 */
    private static final String BEAN_NAME_PREFIX = "datamation:";

    private ApplicationContext applicationContext;
    private final DmTableRepository dmTableRepository;
    private final DmColumnRepository dmColumnRepository;
    private final DmRelationRepository dmRelationRepository;
    /** 记录repository bean定义的map。key为beanName，value为bean定义 */
    Map<String, RootBeanDefinition> repositoryBeanDefinitionMap;
    /** 实体管理工厂 */
    private EntityManagerFactory emf;
    /** 表名与po类的映射 */
    private Map<String, Class<?>> poMap;
    /** 表名与类信息（用于生成po）对象的映射 */
    private Map<String, ClassInfo> poClassInfoMap;
    /** 表名与repository类的映射 */
    private Map<String, Class<?>> repositoryMap;
    /** 表名与表名的关联的映射 */
    private Map<String, Set<String>> tableRelationMap;
    /** 表名与关联对象的映射 */
    private Map<String, DmRelationPO> relationMap;

    public DatamationRepository(DmTableRepository dmTableRepository, DmColumnRepository dmColumnRepository,
                                DmRelationRepository dmRelationRepository) {
        this.dmTableRepository = dmTableRepository;
        this.dmColumnRepository = dmColumnRepository;
        this.dmRelationRepository = dmRelationRepository;
    }

    /**
     * 由表名获取对应的repository
     *
     * @param tableCode 表名
     * @return repository
     */
    public Object getRepository(String tableCode) {
        if (!repositoryMap.containsKey(tableCode)) {
            throw new DatamationException("Not found repository for table " + tableCode);
        }
        return applicationContext.getBean(repositoryMap.get(tableCode));
    }

    /**
     * 获取EntityManagerFactory
     *
     * @return EntityManagerFactory
     */
    public EntityManagerFactory getEntityManagerFactory() {
        return emf;
    }

    /**
     * 由表名获取对应的po class
     *
     * @param tableCode 表名
     * @return po class
     */
    public Class<?> getPoClass(String tableCode) {
        if (!poMap.containsKey(tableCode)) {
            throw new DatamationException("Not found po for table " + tableCode);
        }
        return poMap.get(tableCode);
    }

    /**
     * 由表名获取对应类信息（用于生成po）
     *
     * @param tableCode 表名
     * @return classInfo
     */
    public ClassInfo getPoClassInfo(String tableCode) {
        if (!poClassInfoMap.containsKey(tableCode)) {
            throw new DatamationException("Not found poClassInfo for table " + tableCode);
        }
        return poClassInfoMap.get(tableCode);
    }

    /**
     * 获取两个表之间的关联
     *
     * @param tableCode 表名
     * @param otherTableCode 表名
     * @return 两表间关联
     */
    public List<DmRelationPO> getRelation(String tableCode, String otherTableCode) {
        Set<String> checkedTable = new HashSet<>();
        checkedTable.add(tableCode);
        // 检查两个表间是否有关联
        List<String> tableCodes = checkRelation(tableCode, otherTableCode, checkedTable, new LinkedList<>());
        if (tableCodes.isEmpty()) {
            // 没有关联抛出异常
            throw new DatamationException("There is no correlation between table: " + tableCode + " and table: " + otherTableCode);
        }
        // 补上源表表名
        tableCodes.add(tableCode);
        // 顺序逆转
        Collections.reverse(tableCodes);
        List<DmRelationPO> relations = new ArrayList<>();
        String key;
        for (int i = 1; i < tableCodes.size(); i++) {
            key = tableCodes.get(i - 1) + tableCodes.get(i);
            relations.add(relationMap.get(key));
        }
        return relations;
    }

    /**
     * 检查两个表之间是否有关联
     *
     * @param tableCode 表名
     * @param otherTableCode 表名
     * @param checkedTable 检查过的表名集合
     * @param result 用来接收递归结果的对象
     * @return 两表间所有的关联表（包含目标表表名，不包含源表表名，关联顺序为倒叙）
     */
    private LinkedList<String> checkRelation(String tableCode, String otherTableCode, Set<String> checkedTable,
                                             LinkedList<String> result) {
        if (CollectionUtils.isEmpty(tableRelationMap)) {
            return new LinkedList<>();
        }
        Set<String> relations = tableRelationMap.get(tableCode);
        // 判断是否关联
        if (relations.contains(otherTableCode)) {
            result.add(otherTableCode);
            return result;
        }
        for (String relation : relations) {
            // 跳过已检查过的数据
            if (checkedTable.contains(relation)) {
                continue;
            }
            checkedTable.add(relation);
            // 递归调用，判断是否找到
            if (!org.springframework.util.CollectionUtils.isEmpty(checkRelation(relation, otherTableCode, checkedTable, result))) {
                // 如果找到，设置返回结果
                result.add(relation);
                return result;
            }
        }
        return new LinkedList<>();
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        buildDatamationRepository();
    }

    /**
     * 创建repository前的清空操作
     */
    private void destroyBeforeBuild() {
        // 重置参数值
        poMap = new HashMap<>();
        poClassInfoMap = new HashMap<>();
        repositoryMap = new HashMap<>();
        tableRelationMap = new HashMap<>();
        relationMap = new HashMap<>();
        // 手动关闭emf
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
        // 获取beanFactory用于手动注册bd
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        // 销毁自定义事务
        beanFactory.destroySingleton(BEAN_NAME_PREFIX + SPRING_TRANSACTION_MANAGER);
        if (repositoryBeanDefinitionMap != null) {
            // 销毁所有手动生成的repository
            for (String beanName : repositoryBeanDefinitionMap.keySet()) {
                beanFactory.removeBeanDefinition(beanName);
            }
        }
        // 重置参数值
        repositoryBeanDefinitionMap = new HashMap<>();
        emf = null;
    }

    /**
     * 构建datamationRepository
     */
    public void buildDatamationRepository() {
        // 构建前清空所有
        destroyBeforeBuild();
        // 查询对象
        DmSpecification dmSpecification = DmSpecification.of().add(DmCriteria.equal("delFlag", DEL_FLAG_NORMAL));
        // 查询所有有效table数据
        List<DmTablePO> tableList = dmTableRepository.findAll(DmQuery.createSpecification(dmSpecification));
        // 查询所有有效column数据
        List<DmColumnPO> columnList = dmColumnRepository.findAll(DmQuery.createSpecification(dmSpecification));
        // 将所有column按照tableCode分组
        Map<String, Set<DmColumnPO>> columnMap = columnList.stream()
            .collect(Collectors.groupingBy(DmColumnPO::getTableCode, toSet()));
        // 查询所有table间关联数据
        List<DmRelationPO> relationList = dmRelationRepository.findAll();
        if (CollectionUtils.isNotEmpty(relationList)) {
            // 表名与表名的关联的映射map
            tableRelationMap = new HashMap<>(relationList.size() << 1);
            // 表名与关联对象的映射map
            relationMap = new HashMap<>(relationList.size() << 1);
            for (DmRelationPO relation : relationList) {
                relationMap.put(relation.getSourceTableCode() + "-" + relation.getTargetTableCode(), relation);
                relationMap.put(relation.getTargetTableCode() + "-" + relation.getSourceTableCode(), relation);
                if (!tableRelationMap.containsKey(relation.getSourceTableCode())) {
                    tableRelationMap.put(relation.getSourceTableCode(), new HashSet<>());
                }
                if (!tableRelationMap.containsKey(relation.getTargetTableCode())) {
                    tableRelationMap.put(relation.getTargetTableCode(), new HashSet<>());
                }
                tableRelationMap.get(relation.getSourceTableCode()).add(relation.getTargetTableCode());
                tableRelationMap.get(relation.getTargetTableCode()).add(relation.getSourceTableCode());
            }
        }

        // 自定义类加载器
        DmClassLoader dmClassLoader = new DmClassLoader();
        // 动态生成po类
        PoConverter poConverter = new PoConverter();
        PoGenerator poGenerate = new PoGenerator(dmClassLoader);
        ClassLoader poClassLoader = poGenerate.getNeighborClassLoader();
        ClassInfo poClassInfo;
        Class<?> poClass;
        // 动态生成repository类
        RepositoryConverter repositoryConverter;
        RepositoryGenerator repositoryGenerate = new RepositoryGenerator(dmClassLoader);
        Class<?> repositoryClass;

        // 获取beanFactory用于手动注册bd
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        // 实体类集合
        List<Class<?>> entities = new ArrayList<>();
        // 记录repository bean定义的map。key为beanName，value为bean定义
        repositoryBeanDefinitionMap = new HashMap<>(tableList.size() << 1);
        String repositoryName;
        RootBeanDefinition repositoryBd;
        // bean定义构建原型
        String tempBeanName = beanFactory.getBeanNamesForType(DmTableRepository.class)[0];
        // 设置自定义类加载器到spring容器
        beanFactory.setBeanClassLoader(dmClassLoader);
        RootBeanDefinition tempBeanDefinition = (RootBeanDefinition) beanFactory.getBeanDefinition(tempBeanName);
        // 遍历所有table
        for (DmTablePO table : tableList) {
            // 将所有column关联到对应的table
            table.getColumns().addAll(columnMap.get(table.getCode()));
            // 获取生成po类所需要的信息
            poClassInfo = poConverter.getClassInfo(table);
            // 生成po类
            poClass = poGenerate.generate(poClassInfo);
            // 将po类添加到实体类集合，供hibernate加载
            entities.add(poClass);
            // 添加表名与po类的映射
            poMap.put(table.getCode(), poClass);
            // 添加表名与类信息的映射
            poClassInfoMap.put(table.getCode(), poClassInfo);

            // 初始化repository转换器
            repositoryConverter = new RepositoryConverter(poClass);
            // 生成repository类
            repositoryClass = repositoryGenerate.generate(repositoryConverter.getClassInfo(table));
            // 获取已加载到spring中的repository bean定义，作为原型
            repositoryBd = new RootBeanDefinition();
            repositoryBd.overrideFrom(tempBeanDefinition);
            repositoryBd.getConstructorArgumentValues().addIndexedArgumentValue(0, repositoryClass);
            repositoryBd.setAttribute("factoryBeanObjectType", repositoryClass.getName());
            // 获取bean name
            repositoryName = BEAN_NAME_PREFIX + uncapitalize(repositoryClass.getSimpleName());
            // 将bean定义放入map中
            repositoryBeanDefinitionMap.put(repositoryName, repositoryBd);
            // 添加表名与repository类的映射
            repositoryMap.put(table.getCode(), repositoryClass);
        }

        // 判断是否有生成实体对象
        if (!entities.isEmpty()) {
            // 创建EntityManagerFactory
            emf = createEntityManagerFactory(beanFactory, poClassLoader, entities);
            // 以下代码为repository注入容器处理
            registerRepository(beanFactory, emf, repositoryBeanDefinitionMap);
        }
    }

    /**
     * 创建EntityManagerFactory
     *
     * @param beanFactory bean工厂对象
     * @param classLoader 类加载器
     * @param entities 实体类集合
     * @return EntityManagerFactory
     */
    private EntityManagerFactory createEntityManagerFactory(DefaultListableBeanFactory beanFactory,
                                                            ClassLoader classLoader, List<Class<?>> entities) {
        // jpa配置
        Properties properties = new Properties();
        // 读取spring容器中的jpa配置，并设置到当前jpa配置对象中
        properties.putAll(beanFactory.getBean(JpaProperties.class).getProperties());
        // 创建持久单元信息
        PersistenceUnitInfoImpl persistenceUnitInfo = new PersistenceUnitInfoImpl(
            "default",
            properties
        );
        // 设置数据源
        persistenceUnitInfo.setNonJtaDataSource(beanFactory.getBean(DataSource.class));
        // 默认hibernate配置
        Map<String, Object> map = new HashMap<>();
        map.put("hibernate.ejb.loaded.classes", entities);
        map.put("hibernate.hbm2ddl.auto", "update");
        map.put("hibernate.show_sql", "true");
        CollectionUtils.mergePropertiesIntoMap(properties, map);
        EntityManagerFactoryBuilderImpl emfb = new EntityManagerFactoryBuilderImpl(persistenceUnitInfo, map, classLoader);
        return emfb.build();
    }

    /**
     * 注册repository到spring容器
     *
     * @param beanFactory bean工厂对象
     * @param emf 实体管理工厂
     * @param repositoryBeanDefinitionMap 记录repository bean定义的map。key为beanName，value为bean定义
     */
    private void registerRepository(DefaultListableBeanFactory beanFactory, EntityManagerFactory emf,
                                    Map<String, RootBeanDefinition> repositoryBeanDefinitionMap) {
        // 因EntityManagerFactory是手动初始化的，所以与之关联的事务管理也需要手动创建并注册
        beanFactory.registerSingleton(BEAN_NAME_PREFIX + SPRING_TRANSACTION_MANAGER, transactionManager(emf));

        RootBeanDefinition bd;
        RootBeanDefinition entityManagerBd;
        PropertyValue pv;
        // 遍历所有要注册到spring容器的repository定义
        for (Map.Entry<String, RootBeanDefinition> rbd : repositoryBeanDefinitionMap.entrySet()) {
            bd = rbd.getValue();
            // 替换EntityManagerFactory
            for (int i = 0; i < bd.getPropertyValues().getPropertyValueList().size(); i++) {
                pv = bd.getPropertyValues().getPropertyValueList().get(i);
                // 替换entityManager属性中，org.springframework.orm.jpa.SharedEntityManagerCreator定义中构造方法参数中的
                // EntityManagerFactory。之所以不直接替换entityManager，而是替换entityManagerFactory，
                // 是因为要将entityManager的创建和关闭交给spring容器管理
                if (SPRING_ENTITY_MANAGER.equals(pv.getName())) {
                    entityManagerBd = (RootBeanDefinition) pv.getValue();
                    if (entityManagerBd != null) {
                        entityManagerBd.getConstructorArgumentValues().addIndexedArgumentValue(0, emf);
                        bd.getPropertyValues().getPropertyValueList().set(i, new PropertyValue(pv, entityManagerBd));
                    }
                }
                // 将事务管理器改为自定义的事务管理器
                if (SPRING_TRANSACTION_MANAGER.equals(pv.getName())) {
                    bd.getPropertyValues().getPropertyValueList().set(i,
                        new PropertyValue(SPRING_TRANSACTION_MANAGER, BEAN_NAME_PREFIX + SPRING_TRANSACTION_MANAGER));
                }
            }
            // 将repository注入容器中
            beanFactory.registerBeanDefinition(rbd.getKey(), bd.cloneBeanDefinition());
        }
    }

    /**
     * 生成事务管理器
     *
     * @param entityManagerFactory 实体管理工厂
     * @return 事务管理器
     */
    private PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        JpaTransactionManager txManager = new JpaTransactionManager();
        txManager.setEntityManagerFactory(entityManagerFactory);
        return txManager;
    }

}