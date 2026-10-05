package com.abchina.llmalf.agentgate.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Logic 层内存排序.
 *
 * <p>对齐 Python storage/mysql.py::_ordered:主字段排序 + id 次键
 * (id 恒升序),避免 MySQL LONGTEXT 排序截断。</p>
 */
final class StorageOrdering {

    private StorageOrdering() {
    }

    /**
     * 按字段排序,id 为稳定次键(升序).
     *
     * @param items 待排序列表(不修改原列表)
     * @param field 主字段取值函数
     * @param descending 主字段是否降序
     * @param id id 取值函数
     * @param <T> 元素类型
     * @param <F> 主字段类型
     * @return 排序后的新列表
     */
    static <T, F extends Comparable<F>> List<T> ordered(List<T> items,
            Function<T, F> field, boolean descending, Function<T, String> id) {
        Comparator<T> comparator = Comparator.comparing(field);
        if (descending) {
            comparator = comparator.reversed();
        }
        comparator = comparator.thenComparing(id);
        List<T> sorted = new ArrayList<>(items);
        sorted.sort(comparator);
        return sorted;
    }

    /**
     * 按字段降序、id 升序排序(默认时序).
     *
     * @param items 待排序列表
     * @param field 主字段取值函数
     * @param id id 取值函数
     * @param <T> 元素类型
     * @param <F> 主字段类型
     * @return 排序后的新列表
     */
    static <T, F extends Comparable<F>> List<T> ordered(List<T> items,
            Function<T, F> field, Function<T, String> id) {
        return ordered(items, field, true, id);
    }
}
