package com.kltyton.autoseamblend.runtime.geometry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 中文：按源元素写时复制；只有实际增删或替换时才分配新列表。
 * <p>
 * English: Copy-on-write output for source elements; a replacement list is allocated only after
 * a real addition, removal, or substitution.
 *
 * @param <T> 中文：输出元素类型；English: output element type
 */
public final class IdentityPreservingListBuilder<T> {
    private final List<T> source;
    private ArrayList<T> changed;
    private int sourceIndex = -1;
    private int additions;
    private T firstAddition;

    public IdentityPreservingListBuilder(List<T> source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public void beginSource(int index) {
        if (sourceIndex >= 0
                || index < 0
                || index >= source.size()) {
            throw new IllegalStateException(
                    "invalid output source index");
        }
        sourceIndex = index;
        additions = 0;
        firstAddition = null;
    }

    public void add(T element) {
        Objects.requireNonNull(element, "element");
        if (changed != null) {
            changed.add(element);
        } else if (additions == 0) {
            firstAddition = element;
        } else {
            materializePrefix();
            changed.add(firstAddition);
            changed.add(element);
        }
        additions++;
    }

    public void addAll(List<T> elements) {
        Objects.requireNonNull(elements, "elements");
        if (elements.isEmpty()) {
            return;
        }
        if (changed != null) {
            changed.addAll(elements);
        } else if (additions == 0 && elements.size() == 1) {
            firstAddition = elements.get(0);
        } else {
            materializePrefix();
            if (additions == 1) {
                changed.add(firstAddition);
            }
            changed.addAll(elements);
        }
        additions += elements.size();
    }

    public void endSource() {
        if (sourceIndex < 0) {
            throw new IllegalStateException(
                    "output source was not started");
        }
        if (changed == null
                && (additions != 1
                || firstAddition != source.get(sourceIndex))) {
            materializePrefix();
            if (additions == 1) {
                changed.add(firstAddition);
            }
        }
        sourceIndex = -1;
        additions = 0;
        firstAddition = null;
    }

    public List<T> finish() {
        if (sourceIndex >= 0) {
            throw new IllegalStateException(
                    "output source was not completed");
        }
        return changed == null
                ? source
                : List.copyOf(changed);
    }

    private void materializePrefix() {
        if (changed != null) {
            return;
        }
        changed = new ArrayList<>(source.size() + 1);
        for (int index = 0; index < sourceIndex; index++) {
            changed.add(source.get(index));
        }
    }
}
