package com.kltyton.autoseamblend.frontend.uilib.screen;

import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDraftFields;
import com.kltyton.autoseamblend.frontend.controller.UilibWorkbenchController;
import java.util.Objects;
import java.util.function.Consumer;

/** 中文：Loader 原生候选扫描所需的最小视图生命周期端口。 / English: Minimal view-lifecycle port for Loader-native candidate scanning. */
public interface WorkbenchViewLifecycle<T extends WorkbenchDraftFields> {
    void pickerRequested(UilibWorkbenchController<T> controller);

    void tick(UilibWorkbenchController<T> controller);

    static <T extends WorkbenchDraftFields> WorkbenchViewLifecycle<T> none() {
        return of(ignored -> {}, ignored -> {});
    }

    static <T extends WorkbenchDraftFields> WorkbenchViewLifecycle<T> of(
            Consumer<UilibWorkbenchController<T>> pickerRequested,
            Consumer<UilibWorkbenchController<T>> tick) {
        Objects.requireNonNull(pickerRequested, "pickerRequested");
        Objects.requireNonNull(tick, "tick");
        return new WorkbenchViewLifecycle<>() {
            @Override
            public void pickerRequested(UilibWorkbenchController<T> controller) {
                pickerRequested.accept(controller);
            }

            @Override
            public void tick(UilibWorkbenchController<T> controller) {
                tick.accept(controller);
            }
        };
    }
}
