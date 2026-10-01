package com.sca4d.core.api.logging;

import org.slf4j.Marker;

import java.io.Serial;
import java.util.Collections;
import java.util.Iterator;

/** 声明需要日志后端执行特殊处理且不可修改的 SLF4J Marker。 */
public class LogMark {

    /** 当前真实事务提交后才输出的 INFO 成功日志。 */
    public static final Marker AFTER_COMMIT = new ImmutableMarker("SCA4DJ_AFTER_COMMIT");

    private LogMark() {
    }

    /** 阻止调用方给公共 Marker 增加引用，从而改变其他日志的匹配结果。 */
    private static class ImmutableMarker implements Marker {

        @Serial
        private static final long serialVersionUID = 1L;

        private final String name;

        private ImmutableMarker(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void add(Marker reference) {
            throw new UnsupportedOperationException("公共日志 Marker 不允许增加引用");
        }

        @Override
        public boolean remove(Marker reference) {
            throw new UnsupportedOperationException("公共日志 Marker 不允许删除引用");
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean hasChildren() {
            return false;
        }

        @Override
        public boolean hasReferences() {
            return false;
        }

        @Override
        public Iterator<Marker> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public boolean contains(Marker other) {
            return equals(other);
        }

        @Override
        public boolean contains(String otherName) {
            return name.equals(otherName);
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof Marker marker && name.equals(marker.getName());
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
