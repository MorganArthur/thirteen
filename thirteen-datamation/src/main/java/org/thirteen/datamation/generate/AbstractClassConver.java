package org.thirteen.datamation.generate;

import org.springframework.util.CollectionUtils;
import org.thirteen.datamation.model.po.DmColumnPO;
import org.thirteen.datamation.model.po.DmTablePO;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Aaron.Sun
 * @description 数据库配置转为生成class所需要的信息。抽象
 * @date Created in 16:19 2020/8/10
 * @modified By
 */
public abstract class AbstractClassConver {

    private static final Pattern LINE_PATTERN = Pattern.compile("_(\\w)");

    /**
     * 获取生成class所需要的所有信息
     *
     * @param table 数据库配置信息
     * @return 生成class所需要的所有信息
     */
    public ClassInfo getClassInfo(DmTablePO table) {
        ClassInfo classInfo = this.tableToClass(table);
        classInfo.setAnnotationInfos(this.tableToAnnotation(table));
        FieldInfo fieldInfo;
        if (!CollectionUtils.isEmpty(table.getColumns())) {
            classInfo.setFieldInfos(new ArrayList<>());
            for (DmColumnPO column : table.getColumns()) {
                fieldInfo = this.columnToField(column);
                if (fieldInfo != null) {
                    fieldInfo.setAnnotationInfos(this.columnToAnnotation(column));
                    classInfo.getFieldInfos().add(fieldInfo);
                }
            }
        }
        return classInfo;
    }

    /**
     * table转classInfo
     *
     * @param table 数据库配置表信息
     * @return classInfo（生成class所需要的信息）
     */
    protected abstract ClassInfo tableToClass(DmTablePO table);

    /**
     * table转classInfo
     *
     * @param table 数据库配置表信息
     * @return classInfo（生成class的注解所需要的信息）
     */
    protected abstract List<AnnotationInfo> tableToAnnotation(DmTablePO table);

    /**
     * table转classInfo
     *
     * @param column 数据库配置列信息
     * @return classInfo（生成class中的字段的注解所需要的信息）
     */
    protected abstract List<AnnotationInfo> columnToAnnotation(DmColumnPO column);

    /**
     * column转fieldInfo
     *
     * @param column 数据库配置列信息
     * @return fieldInfo（生成class中的字段所需要的信息）
     */
    protected abstract FieldInfo columnToField(DmColumnPO column);

    /**
     * 将包含下划线的字符串转为驼峰命名（首字母大写）
     *
     * @param str 包含下划线的字符串
     * @return 驼峰命名
     */
    public static String lineToHumpAndStartWithCapitalize(String str) {
        char[] chars = lineToHump(str).toCharArray();
        // 如果为小写字母，则将ascii编码前移32位
        char a = 'a';
        char z = 'z';
        if (chars[0] >= a && chars[0] <= z) {
            chars[0] = (char) (chars[0] - 32);
        }
        return String.valueOf(chars);
    }

    /**
     * 将包含下划线的字符串转为驼峰命名
     *
     * @param str 包含下划线的字符串
     * @return 驼峰命名
     */
    public static String lineToHump(String str) {
        str = str.toLowerCase();
        Matcher matcher = LINE_PATTERN.matcher(str);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, matcher.group(1).toUpperCase());
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}